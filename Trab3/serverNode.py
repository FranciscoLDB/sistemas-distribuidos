import Pyro5.api # type: ignore
import sys
import threading
import random
import time
from enum import Enum

from rich.console import Console # type: ignore
from rich.progress import Progress, BarColumn, TextColumn # type: ignore

# Console do Rich para prints coloridos
console = Console()

# Configuração dos nós pares
NODES_CONFIG = {
    'A': {"id": "raft.node.A", "port": 5001},
    'B': {"id": "raft.node.B", "port": 5002},
    'C': {"id": "raft.node.C", "port": 5003},
    'D': {"id": "raft.node.D", "port": 5004}
}

# Constantes de timeout (em segundos)
ELECTION_TIMEOUT_MIN = 5
ELECTION_TIMEOUT_MAX = 15
HEARTBEAT_INTERVAL = 1
PEER_REQUEST_TIMEOUT = 0.2
PEER_HEARTBEAT_TIMEOUT = 0.3


class RaftState(Enum):
    FOLLOWER = "Follower"
    CANDIDATE = "Candidate"
    LEADER = "Leader"

class LogEntry:
    def __init__(self, term, command):
        self.term = term
        self.command = command

class RaftNode:
    """Implementação de um nó Raft com eleição de líder e heartbeats."""
    
    def __init__(self, node_label, node_id):
        self.node_label = node_label
        self.node_id = node_id
        self.state = RaftState.FOLLOWER.value
        self.timeout = random.uniform(ELECTION_TIMEOUT_MIN, ELECTION_TIMEOUT_MAX)
        self.last_heartbeat = time.time()
        self.peers = {}  # URIs dos pares
        self.uri = f"PYRO:{node_id}@localhost:{NODES_CONFIG[node_label]['port']}"

        # Persistent state on all servers
        self.term = 0
        self.votedFor = None
        self.log = []

        # Volatile state on all servers
        self.commitIndex = 0
        self.lastApplied = 0

        # Volatile state on leaders
        self.nextIndex = {}
        self.matchIndex = {}
        
        self._exibir_inicializacao()
        self.configurar_conexoes()

    def _exibir_inicializacao(self):
        """Exibe mensagem de inicialização formatada."""
        console.print("[bold blue]=======================================================[/bold blue]")
        console.print(f"[bold cyan]>> Nó {self.node_label}[/bold cyan] (ID:{self.node_id}) inicializado como [bold green]Follower[/bold green].")
        console.print(f"[T] Timeout de eleição: [yellow]{self.timeout:.2f}[/yellow] segundos.")
        console.print(f"[@] URI do nó: [white]{self.uri}[/white]")
        console.print("[bold blue]=======================================================[/bold blue]\n")

    def configurar_conexoes(self):
        """Salva as URIs dos pares"""
        for label, info in NODES_CONFIG.items():
            if label != self.node_label:
                uri = f"PYRO:{info['id']}@localhost:{info['port']}"
                self.peers[label] = uri

    def resetar_timeout(self):
        """Reseta o temporizador de heartbeat."""
        self.last_heartbeat = time.time()

    def _solicitar_voto_de_peer(self, label, uri):
        """Solicita voto de um peer específico. Retorna True se voto concedido."""
        try:
            with Pyro5.api.Proxy(uri) as proxy:
                proxy._pyroTimeout = PEER_REQUEST_TIMEOUT
                console.print(f"[blue][>] Solicitando voto de {label}...[/blue]")
                resposta = proxy.request_vote(self.term)
                
                if resposta:
                    console.print(f"[green][OK] Voto recebido de {label}[/green]")
                return resposta
        
        except Pyro5.errors.CommunicationError:
            console.print(f"[red][!] Nó {label} offline.[/red]")
        except Pyro5.errors.TimeoutError:
            console.print(f"[yellow][~] Tempo esgotado ao contactar o nó {label}.[/yellow]")
        except Exception as e:
            console.print(f"[bold red][X] Erro no nó {label} [{type(e).__name__}][/bold red]")
        
        return False

    def _contar_votos(self):
        """Conta votos de todos os peers. Retorna número total incluindo voto próprio."""
        votos_recebidos = 1  # Voto próprio
        
        for label, uri in self.peers.items():
            if self._solicitar_voto_de_peer(label, uri):
                votos_recebidos += 1
        
        return votos_recebidos

    def _verificar_vitoria_eleicao(self, votos_recebidos):
        """Verifica se há maioria de votos e torna-se líder se apropriado."""
        maioria = len(NODES_CONFIG) // 2
        
        if votos_recebidos > maioria:
            self.tornar_se_lider()
        else:
            console.print(f"[yellow][-] Votos insuficientes ({votos_recebidos}). Retornando a Follower.[/yellow]\n")
            self.state = RaftState.FOLLOWER.value

    def solicitar_votos(self):
        """Inicia eleição solicitando votos de todos os peers."""
        console.print(f"\n[bold magenta][*] Iniciando eleição para termo {self.term}...[/bold magenta]")
        self.resetar_timeout()
        self.term += 1
        
        votos_recebidos = self._contar_votos()
        self._verificar_vitoria_eleicao(votos_recebidos)

    @Pyro5.api.expose
    def request_vote(self, candidate_term):
        """Processa requisição de voto de um candidato."""
        if candidate_term > self.term:
            console.print(f"[cyan][V] Concedendo voto para termo {candidate_term}.[/cyan]")
            self.term = candidate_term
            self.state = RaftState.FOLLOWER.value
            self.resetar_timeout()
            return True
        return False

    def _calcular_cor_estado(self):
        """Retorna a cor da barra de progresso baseado no estado atual."""
        if self.state == RaftState.FOLLOWER.value:
            return "green"
        elif self.state == RaftState.CANDIDATE.value:
            return "magenta"
        else:  # LEADER
            return "yellow"

    def _construir_descricao_progresso(self, decorrido):
        """Constrói descrição formatada para a barra de progresso."""
        color = self._calcular_cor_estado()
        return f"[{color}]Estado: {self.state:<9}[/{color}] | Termo: {self.term} | Timeout: {self.timeout:.1f}s"

    def _processar_timeout_eleicao(self):
        """Processa timeout de eleição quando é follower."""
        console.print(f"\n[bold red][!] TIMEOUT ATINGIDO![/bold red]")
        self.solicitar_votos()

    def _loop_heartbeat(self):
        """Executa em uma thread separada enviando heartbeats continuamente enquanto for líder."""
        console.print(f"\n[bold blue][>] Thread exclusiva de heartbeats iniciada![/bold blue]")
        
        while self.state == RaftState.LEADER.value:
            self.send_heartbeat()
            self.resetar_timeout()  # Atualiza o tempo para a barra de progresso não quebrar
            time.sleep(HEARTBEAT_INTERVAL)
            
        console.print(f"\n[yellow][!] O nó {self.node_label} deixou de ser Leader. Encerrando thread de heartbeats.[/yellow]")

    def loop_eleicao(self):
        """Loop principal que monitora timeouts e gerencia eleições."""
        with Progress(
            TextColumn("[bold blue]{task.description}"),
            BarColumn(bar_width=40),
            TextColumn("[progress.percentage]{task.percentage:>3.0f}%"),
            console=console
        ) as progress:
            
            task_id = progress.add_task(f"Status ({self.node_label})", total=self.timeout)

            while True:
                time.sleep(0.1)
                
                decorrido = time.time() - self.last_heartbeat
                
                progress.update(
                    task_id, 
                    completed=min(decorrido, self.timeout), 
                    description=self._construir_descricao_progresso(decorrido)
                )

                if self.state == RaftState.FOLLOWER.value:
                    if decorrido > self.timeout:
                        self._processar_timeout_eleicao()
                        progress.reset(task_id, total=self.timeout)
                
                elif self.state == RaftState.LEADER.value:
                    if decorrido > self.timeout:
                        progress.reset(task_id, total=self.timeout)
                        self.resetar_timeout()

    def _registrar_no_nameserver(self):
        """Tenta registrar o nó como líder no nameserver."""
        try:
            ns = Pyro5.api.locate_ns()
            ns.register("raft.leader", self.uri)
            console.print("[green][NET] Leader registrado no Name Server (pyro5-ns) com sucesso![/green]")
        except Exception:
            console.print("[grey]Aviso: Name Server não encontrado na rede. Ignorando registro...[/grey]")

    def tornar_se_lider(self):
        """Transição do nó para estado de líder."""
        self.state = RaftState.LEADER.value
        console.print(f"\n[bold yellow on blue] *** NÓ {self.node_label} VENCEU A ELEIÇÃO E AGORA É O Leader! *** [/bold yellow on blue]\n")
        
        # Inicia thread de heartbeat exclusiva para líderes
        threading.Thread(target=self._loop_heartbeat, daemon=True).start()
        self._registrar_no_nameserver()

    @Pyro5.api.expose
    @Pyro5.api.oneway
    def heartbeat(self, term, leaderId, prevLogIndex, prevLogTerm, leaderCommit):
        """Processa heartbeat recebido do líder."""
        #console.print(f"[blue][<] Recebido heartbeat de {leaderId} (Termo {term})[/blue]")

        if term > self.term or self.state != RaftState.FOLLOWER.value:
            console.print(f"[bold green][+] Reconhecendo {leaderId} como novo Leader (Termo {term})[/bold green]")
        
        self.resetar_timeout()
        if term >= self.term:
            self.term = term
            self.state = RaftState.FOLLOWER.value

    def _calcular_info_log(self):
        """Calcula prevLogIndex e prevLogTerm baseado no log."""
        prev_log_index = len(self.log) - 1
        prev_log_term = self.log[prev_log_index].term if prev_log_index >= 0 else 0
        return prev_log_index, prev_log_term

    def send_heartbeat(self):
        """Envia heartbeat para todos os peers."""
        prev_log_index, prev_log_term = self._calcular_info_log()

        for label, uri in self.peers.items():
            try:
                with Pyro5.api.Proxy(uri) as proxy:
                    #console.print(f"[blue][>] Enviando heartbeat para {label}...[/blue]")
                    proxy._pyroTimeout = PEER_HEARTBEAT_TIMEOUT
                    proxy.heartbeat(self.term, self.node_label, prev_log_index, prev_log_term, self.commitIndex)
            except Exception as e:
                #console.print(f"[red][!] Falha ao enviar heartbeat para {label}.[/red]")
                #console.print(f"[red][!] Erro: {e}[/red]")
                pass

def _exibir_menu_inicial():
    """Exibe menu de seleção de nó."""
    console.print("\n[bold cyan]================================[/bold cyan]")
    console.print("[bold cyan]=   Inicialização do Nó Raft   =[/bold cyan]")
    for label, info in NODES_CONFIG.items():
        console.print(f"  [bold white][{label}][/bold white] - Porta {info['port']}")
    console.print("[bold cyan]================================[/bold cyan]\n")


def _obter_escolha_usuario():
    """Obtém e valida escolha do usuário. Retorna a letra escolhida ou None se inválida."""
    _exibir_menu_inicial()
    escolha = input("Escolha a letra do processo para iniciar (A, B, C ou D): ").upper()
    
    if escolha not in NODES_CONFIG:
        console.print("[bold red]Escolha inválida! Saindo...[/bold red]")
        return None
    
    return escolha


def _iniciar_node_com_daemon(escolha):
    """Cria daemon, registra nó e inicia threads necessárias."""
    config = NODES_CONFIG[escolha]
    daemon = Pyro5.api.Daemon(port=config['port'], host="localhost")
    
    node = RaftNode(escolha, config['id'])
    daemon.register(node, objectId=config['id'])
    
    # Inicia thread de eleição
    thread_eleicao = threading.Thread(target=node.loop_eleicao, daemon=True)
    thread_eleicao.start()
    
    return daemon


def iniciar_servidor():
    """Função principal para iniciar o servidor Raft."""
    escolha = _obter_escolha_usuario()
    
    if escolha is None:
        return
    
    daemon = _iniciar_node_com_daemon(escolha)
    daemon.requestLoop()

if __name__ == "__main__":
    iniciar_servidor()