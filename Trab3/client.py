import Pyro5.api # type: ignore
import time
from rich.console import Console # type: ignore

console = Console()

def conectar_lider():
    """Busca o líder no serviço de nomes do PyRO."""
    try:
        # Localiza o Name Server na rede local
        ns = Pyro5.api.locate_ns()
        
        # Pesquisa o URI do líder que foi registrado pelo servidor
        uri = ns.lookup("raft.leader")
        return Pyro5.api.Proxy(uri)
        
    except Pyro5.errors.NamingError:
        console.print("[yellow][!] Nenhum líder encontrado no Name Server. Aguardando eleição...[/yellow]")
        return None
    except Exception as e:
        console.print(f"[bold red][X] Erro ao conectar com o Name Server: {e}[/bold red]")
        return None

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

            lider = conectar_lider()
            
            if lider:
                console.print(f"[cyan][>] Enviando comando para o líder...[/cyan]")
                
                # Invoca o método no líder
                resposta = lider.receber_comando(comando)
                console.print(f"[bold green][V] Resposta do líder:[/bold green] {resposta}\n")
                
        except Pyro5.errors.CommunicationError:
            console.print("[bold red][!] Falha de comunicação. O líder atual pode ter falhado. O cluster fará uma nova eleição.[/bold red]\n")
        except AttributeError:
             console.print("[bold red][!] O líder foi encontrado, mas falta implementar o método 'receber_comando' na classe RaftNode do servidor![/bold red]\n")
        except KeyboardInterrupt:
            console.print("\n[cyan]Encerrando cliente...[/cyan]")
            break

if __name__ == "__main__":
    iniciar_cliente()