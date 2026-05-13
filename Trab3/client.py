import Pyro5.api # type: ignore
from rich.console import Console # type: ignore

# =========================================================================
# BLOCO 1: CONFIGURAÇÕES E VARIÁVEIS GLOBAIS
# =========================================================================

# Console do Rich para prints formatados e coloridos
console = Console()

# Endereço fixo do servidor de nomes (Name Server)
NAME_SERVER_HOST = "localhost"
NAME_SERVER_PORT = 9090


# =========================================================================
# BLOCO 2: COMUNICAÇÃO COM O CLUSTER (RAFT)
# =========================================================================

def _enviar_comando_direto(comando):
    """
    Conecta diretamente ao Name Server, descobre quem é o líder atual, 
    envia o comando e encerra a conexão. Tudo em um único fluxo (Stateless).
    """
    try:
        # 1. Conecta ao Name Server
        ns = Pyro5.api.locate_ns(host=NAME_SERVER_HOST, port=NAME_SERVER_PORT)
        
        # 2. Pergunta ao Name Server qual é a URI do líder atual do cluster
        uri = ns.lookup("raft.leader")
        
        # 3. Abre conexão com o líder, envia o comando e fecha imediatamente
        with Pyro5.api.Proxy(uri) as lider:
            resposta = lider.receber_comando(comando)
            return resposta
            
    except Pyro5.errors.NamingError:
        return "[yellow][!] Nenhum líder registrado no Name Server no momento. O cluster pode estar em eleição.[/yellow]"
    except Pyro5.errors.CommunicationError:
        return "[bold red][!] Falha de comunicação com o Líder ou Name Server. Verifique se estão online.[/bold red]"
    except AttributeError:
        return "[bold red][!] Método 'receber_comando' não implementado no servidor![/bold red]"
    except Exception as e:
        return f"[bold red][X] Erro inesperado: {e}[/bold red]"


# =========================================================================
# BLOCO 3: INTERFACE DO USUÁRIO E LOOP PRINCIPAL
# =========================================================================

def _exibir_cabecalho():
    """Exibe a mensagem de inicialização do cliente."""
    console.print("[bold blue]=======================================================[/bold blue]")
    console.print("[bold cyan]>> Cliente Raft Inicializado[/bold cyan]")
    console.print("[bold blue]=======================================================[/bold blue]\n")

def iniciar_cliente():
    """Loop principal que captura o input do usuário e interage com o cluster."""
    _exibir_cabecalho()
    
    while True:
        try:
            # Captura a mensagem digitada no terminal
            comando = input("Digite o comando para enviar ao log (ou 'sair' para encerrar): ")
            
            # Condição de saída
            if comando.lower() == 'sair':
                console.print("[cyan]Encerrando cliente...[/cyan]")
                break
            
            # Evita o envio de comandos vazios
            if not comando.strip():
                continue

            console.print(f"[cyan][>] Enviando comando para a rede...[/cyan]")
            
            # Delega o envio para a função de rede e aguarda a resposta
            resposta = _enviar_comando_direto(comando)
            
            # Exibe o retorno vindo do servidor Líder
            console.print(f"[bold green][V] Resposta do líder:[/bold green] {resposta}\n")
            
        except KeyboardInterrupt:
            # Permite que o usuário feche usando Ctrl+C sem estourar erros feios na tela
            console.print("\n[cyan]Encerrando cliente (Ctrl+C detectado)...[/cyan]")
            break


# =========================================================================
# PONTO DE ENTRADA
# =========================================================================

if __name__ == "__main__":
    iniciar_cliente()