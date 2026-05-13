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
        self.commitIndex = -1 # Começa em -1 indicando que nenhum índice foi comitado
        self.lastApplied = -1

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
        self.resetar_timeout()
        self.term += 1
        console.print(f"\n[bold magenta][*] Iniciando eleição para termo {self.term}...[/bold magenta]")
        
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
        """Constrói descrição formatada para a barra de progresso em duas linhas."""
        color = self._calcular_cor_estado()
        
        # Extrai apenas a string do comando de cada entrada do log para exibição visual
        comandos_log = [entrada.command for entrada in self.log]
        
        # Monta a primeira linha com dados do nó e do temporizador
        linha1 = f"[{color}]Estado: {self.state:<9}[/{color}] | Termo: {self.term} | Timeout: {self.timeout:.1f}s"
        
        # Monta a segunda linha com dados de consenso e replicação
        linha2 = f"Commit: {self.commitIndex} | Log: {comandos_log}"
        
        # Retorna unindo as duas com uma quebra de linha
        return f"{linha1}\n{linha2}"

    def _processar_timeout_eleicao(self):
        """Processa timeout de eleição quando é follower."""
        console.print(f"\n[bold red][!] TIMEOUT ATINGIDO![/bold red]")
        self.solicitar_votos()

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
        
        # Inicializa variáveis de acompanhamento do log para cada seguidor (§5.3)
        self.nextIndex = {label: len(self.log) for label in self.peers}
        self.matchIndex = {label: -1 for label in self.peers}

        console.print(f"\n[bold yellow on blue] *** NÓ {self.node_label} VENCEU A ELEIÇÃO E AGORA É O Leader! *** [/bold yellow on blue]\n")
        
        # Inicia thread de heartbeat exclusiva para líderes
        threading.Thread(target=self._loop_heartbeat, daemon=True).start()
        self._registrar_no_nameserver()
        
    def _atualizar_commit_index(self):
        """Raft §5.3: Verifica se uma entrada foi replicada na maioria e avança o commitIndex."""
        # Pega todos os matchIndexes e adiciona o log do próprio líder (que é len(self.log) - 1)
        todos_indices = list(self.matchIndex.values())
        todos_indices.append(len(self.log) - 1)
        
        # Ordena de forma decrescente para achar a mediana
        todos_indices.sort(reverse=True)
        
        # O índice que a maioria atingiu estará exatamente no meio do vetor ordenado
        maioria_idx = len(NODES_CONFIG) // 2
        N = todos_indices[maioria_idx]
        
        # Regra do Raft: Só commita se N > commitIndex e a entrada N é do termo atual
        if N > self.commitIndex and N >= 0 and self.log[N].term == self.term:
            self.commitIndex = N
            console.print(f"[magenta][^] QUÓRUM ATINGIDO! Commit Index avançou para {self.commitIndex}[/magenta]")

    def _sincronizar_peer(self, label, uri):
        """Envia heartbeats e sincroniza nós defasados usando o nextIndex."""
        try:
            with Pyro5.api.Proxy(uri) as proxy:
                proxy._pyroTimeout = PEER_HEARTBEAT_TIMEOUT
                
                # 1. Pega o nextIndex que o líder acha que este peer tem
                next_idx = self.nextIndex.get(label, len(self.log))
                
                # 2. Calcula os parâmetros de consistência
                prev_log_index = next_idx - 1
                prev_log_term = self.log[prev_log_index].term if prev_log_index >= 0 else 0
                
                # 3. Pega todas as entradas a partir do next_idx (enviando como dicionários)
                entradas_para_enviar = [{"term": e.term, "command": e.command} for e in self.log[next_idx:]]
                
                # 4. Dispara o RPC AppendEntries
                sucesso = proxy.append_entries(
                    self.term, self.node_label, prev_log_index, prev_log_term, 
                    entradas_para_enviar, self.commitIndex
                )
                
                # 5. Avalia a resposta do seguidor
                if sucesso:
                    if entradas_para_enviar:
                        # O seguidor aceitou os logs atrasados! Atualiza os dicionários.
                        self.nextIndex[label] = next_idx + len(entradas_para_enviar)
                        self.matchIndex[label] = self.nextIndex[label] - 1
                        console.print(f"[green][+] Nó {label} atualizado com sucesso até o log {self.matchIndex[label]}[/green]")
                        
                        # Tenta comitar caso essa atualização atinja a maioria
                        self._atualizar_commit_index() 
                else:
                    # REJEIÇÃO! O seguidor não tem o prevLogIndex. 
                    # Reduz o nextIndex para dar um passo para trás na próxima tentativa.
                    novo_next_idx = max(0, next_idx - 1)
                    self.nextIndex[label] = novo_next_idx
                    
        except Exception:
            # Nó offline. O nextIndex fica congelado esperando ele voltar.
            pass

    def _loop_heartbeat(self):
        """Executa em uma thread separada enviando heartbeats/sincronizações continuamente."""
        console.print(f"\n[bold blue][>] Thread de sincronização do Líder iniciada![/bold blue]")
        
        while self.state == RaftState.LEADER.value:
            for label, uri in self.peers.items():
                # Usa threads separadas para não travar o líder se um nó estiver lento
                threading.Thread(target=self._sincronizar_peer, args=(label, uri), daemon=True).start()
            
            self.resetar_timeout()
            time.sleep(HEARTBEAT_INTERVAL)
            
        console.print(f"\n[yellow][!] O nó {self.node_label} deixou de ser Leader. Encerrando thread de sincronização.[/yellow]")

    @Pyro5.api.expose
    def append_entries(self, term, leaderId, prevLogIndex, prevLogTerm, entries, leaderCommit):
        """Processa append entries RPC recebido do líder."""
        self.resetar_timeout()
        if term >= self.term:
            self.term = term
            self.state = RaftState.FOLLOWER.value

        qtd_entradas = len(entries)
        if qtd_entradas > 0:
            console.print(f"\n[cyan][<] AppendEntries recebido de {leaderId} | PrevLogIndex: {prevLogIndex} | Novas Entradas: {qtd_entradas}[/cyan]")
        
        # 1. Reply false if term < currentTerm (§5.1)
        if term < self.term:
            return False
        
        # 2. Reply false if log doesn’t contain an entry at prevLogIndex whose term matches prevLogTerm (§5.3)
        if prevLogIndex >= 0:
            if prevLogIndex >= len(self.log):
                if qtd_entradas > 0: console.print(f"[yellow][!] Rejeitado: Faltam entradas anteriores ao índice {prevLogIndex}.[/yellow]")
                return False
            if self.log[prevLogIndex].term != prevLogTerm:
                if qtd_entradas > 0: console.print(f"[yellow][!] Rejeitado: O termo no índice {prevLogIndex} está em conflito.[/yellow]")
                return False
        
        # Converte dicionários recebidos para objetos LogEntry
        entradas_obj = [LogEntry(e["term"], e["command"]) if isinstance(e, dict) else e for e in entries]
        
        # 3 e 4. Procurar conflitos e anexar apenas as novas entradas
        novas_entradas_iniciar_em = 0
        for i, entry in enumerate(entradas_obj):
            index = prevLogIndex + 1 + i
            if index < len(self.log):
                if self.log[index].term != entry.term:
                    console.print(f"[bold red][!] Conflito no índice {index}! Removendo entradas antigas...[/bold red]")
                    self.log = self.log[:index]
                    break
            else:
                novas_entradas_iniciar_em = i
                break
        else:
            novas_entradas_iniciar_em = len(entradas_obj)

        entradas_para_adicionar = entradas_obj[novas_entradas_iniciar_em:]
        
        if entradas_para_adicionar:
            self.log.extend(entradas_para_adicionar)
            console.print(f"[green][+] Anexadas {len(entradas_para_adicionar)} nova(s) entrada(s).[/green]")

        # 5. If leaderCommit > commitIndex, set commitIndex = min(...)
        if leaderCommit > self.commitIndex:
            novo_commit = min(leaderCommit, len(self.log) - 1)
            if novo_commit > self.commitIndex:
                console.print(f"[magenta][^] Commit Index avançou de {self.commitIndex} para {novo_commit}[/magenta]")
            self.commitIndex = novo_commit
            
        return True

    @Pyro5.api.expose
    def receber_comando(self, comando):
        """Recebe o comando do cliente de forma assíncrona. (Apenas anexa ao log local)."""
        console.print(f"\n[cyan][>] Comando recebido do cliente: '{comando}'[/cyan]")
        
        if self.state != RaftState.LEADER.value:
            return "Erro: Nó não é o líder atual."
        
        # O Líder apenas anexa ao seu próprio log
        nova_entrada = LogEntry(self.term, comando)
        self.log.append(nova_entrada)
        
        # Retorna imediatamente. A thread de sincronização (loop_heartbeat) 
        # vai perceber o novo log e enviar nos próximos milissegundos usando o nextIndex
        return f"Comando enfileirado com sucesso no termo {self.term}. Aguardando Quórum em background..."


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