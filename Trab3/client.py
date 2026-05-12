import Pyro5.api # type: ignore
from rich.console import Console # type: ignore

console = Console()

NAME_SERVER_HOST = "localhost"
NAME_SERVER_PORT = 9090

def enviar_comando_direto(comando):
    """Conecta no Name Server, pega o líder e envia."""
    try:
        # 1. Conecta ao Name Server
        ns = Pyro5.api.locate_ns(host=NAME_SERVER_HOST, port=NAME_SERVER_PORT)
        
        # 2. Pega a URI do líder atual
        uri = ns.lookup("raft.leader")
        
        # 3. Conecta no líder, envia o comando e fecha a conexão
        with Pyro5.api.Proxy(uri) as lider:
            resposta = lider.receber_comando(comando)
            return resposta
            
    except Pyro5.errors.NamingError:
        return "[yellow][!] Nenhum líder registrado no Name Server no momento.[/yellow]"
    except Pyro5.errors.CommunicationError:
        return "[bold red][!] Falha de comunicação com o Líder ou Name Server.[/bold red]"
    except AttributeError:
        return "[bold red][!] Método 'receber_comando' não implementado no servidor![/bold red]"
    except Exception as e:
        return f"[bold red][X] Erro: {e}[/bold red]"

def iniciar_cliente():
    console.print("[bold blue]=======================================================[/bold blue]")
    console.print("[bold cyan]>> Cliente Raft Inicializado[/bold cyan]")
    console.print("[bold blue]=======================================================[/bold blue]\n")
    
    while True:
        try:
            comando = input("Digite o comando para enviar ao log (ou 'sair' para encerrar): ")
            if comando.lower() == 'sair':
                console.print("[cyan]Encerrando cliente...[/cyan]")
                break
            
            if not comando.strip():
                continue

            console.print(f"[cyan][>] Enviando comando...[/cyan]")
            
            # Envia e já recebe a resposta na hora
            resposta = enviar_comando_direto(comando)
            
            console.print(f"[bold green][V] Resposta do líder:[/bold green] {resposta}\n")
            
        except KeyboardInterrupt:
            console.print("\n[cyan]Encerrando cliente...[/cyan]")
            break

if __name__ == "__main__":
    iniciar_cliente()