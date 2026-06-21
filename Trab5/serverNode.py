import sys
import threading
import random
import time
from enum import Enum
from concurrent import futures

import grpc
import raft_pb2
import raft_pb2_grpc

from rich.console import Console # type: ignore
from rich.progress import Progress, BarColumn, TextColumn # type: ignore

console = Console()

# O NODES_CONFIG agora guarda apenas os IPs e portas que o gRPC usará
NODES_CONFIG = {
    'A': {"id": "raft.node.A", "port": 5001},
    'B': {"id": "raft.node.B", "port": 5002},
    'C': {"id": "raft.node.C", "port": 5003},
    'D': {"id": "raft.node.D", "port": 5004}
}

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

# Mudança crucial: Agora herdamos do Servicer gerado pelo gRPC
class RaftNode(raft_pb2_grpc.RaftServiceServicer):
    
    def __init__(self, node_label, node_id):
        self.node_label = node_label
        self.node_id = node_id
        self.state = RaftState.FOLLOWER.value
        self.timeout = random.uniform(ELECTION_TIMEOUT_MIN, ELECTION_TIMEOUT_MAX)
        self.last_heartbeat = time.time()
        self.peers = {}  # Vai guardar os endereços "localhost:porta" dos outros nós

        self.term = 0
        self.votedFor = None
        self.log = []

        self.commitIndex = 0
        self.lastApplied = 0

        self.nextIndex = {}
        self.matchIndex = {}
        
        self._exibir_inicializacao()
        self._configurar_conexoes()

    def _exibir_inicializacao(self):
        console.print("[bold blue]=======================================================[/bold blue]")
        console.print(f"[bold cyan]>> Nó {self.node_label}[/bold cyan] (ID:{self.node_id}) inicializado como [bold green]Follower[/bold green].")
        console.print(f"[T] Timeout de eleição: [yellow]{self.timeout:.2f}[/yellow] segundos.")
        console.print(f"[@] Porta gRPC: [white]{NODES_CONFIG[self.node_label]['port']}[/white]")
        console.print("[bold blue]=======================================================[/bold blue]\n")

    def _configurar_conexoes(self):
        """No gRPC, guardamos a string 'host:porta' para criar os canais depois."""
        for label, info in NODES_CONFIG.items():
            if label != self.node_label:
                self.peers[label] = f"localhost:{info['port']}"

    def _resetar_timeout(self):
        self.last_heartbeat = time.time()

    # =========================================================================
    # LÓGICA DE ELEIÇÃO (STUBS CLIENTE gRPC)
    # =========================================================================

    def _processar_timeout_eleicao(self):
        console.print(f"\n[bold red][!] TIMEOUT ATINGIDO![/bold red]")
        self._iniciar_eleicao()

    def _iniciar_eleicao(self):
        self._resetar_timeout()
        self.term += 1
        self.state = RaftState.CANDIDATE.value
        self.votedFor = self.node_label
        console.print(f"\n[bold magenta][*] Iniciando eleição para termo {self.term}...[/bold magenta]")
        
        votos_recebidos = self._contar_votos()
        self._verificar_vitoria_eleicao(votos_recebidos)

    def _contar_votos(self):
        votos_recebidos = 1  # Conta o próprio voto
        for label, address in self.peers.items():
            if self._solicitar_voto_de_peer(label, address):
                votos_recebidos += 1
        return votos_recebidos

    def _solicitar_voto_de_peer(self, label, address):
        """Chama o RPC request_vote no nó remoto usando gRPC."""
        try:
            # Cria o canal gRPC e o Stub (cliente)
            with grpc.insecure_channel(address) as channel:
                stub = raft_pb2_grpc.RaftServiceStub(channel)
                
                console.print(f"[blue][>] Solicitando voto de {label}...[/blue]")
                
                # Monta a mensagem baseada no seu .proto
                request = raft_pb2.VoteRequest(
                    candidate_term=self.term,
                    candidate_id=self.node_label,
                    last_log_index=len(self.log) - 1,
                    last_log_term=self.log[-1].term if self.log else 0
                )
                
                # Executa a chamada assíncrona com timeout
                resposta = stub.request_vote(request, timeout=PEER_REQUEST_TIMEOUT)
                
                # Atualiza termo se o par tiver um termo maior que o seu
                if resposta.term > self.term:
                    self.term = resposta.term
                    self.state = RaftState.FOLLOWER.value
                    self.votedFor = None
                    return False
                    
                if resposta.success:
                    console.print(f"[green][OK] Voto recebido de {label}[/green]")
                    return True
        
        except grpc.RpcError as e:
            if e.code() == grpc.StatusCode.DEADLINE_EXCEEDED:
                console.print(f"[yellow][~] Tempo esgotado ao contactar o nó {label}.[/yellow]")
            else:
                console.print(f"[red][!] Nó {label} offline ou inacessível.[/red]")
        return False

    def _verificar_vitoria_eleicao(self, votos_recebidos):
        maioria = len(NODES_CONFIG) // 2
        if votos_recebidos > maioria and self.state == RaftState.CANDIDATE.value:
            self._tornar_se_lider()
        else:
            console.print(f"[yellow][-] Votos insuficientes ({votos_recebidos}) ou estado alterado. Retornando a Follower.[/yellow]\n")
            self.state = RaftState.FOLLOWER.value

    def _tornar_se_lider(self):
        self.state = RaftState.LEADER.value
        self.nextIndex = {label: len(self.log) for label in self.peers}
        self.matchIndex = {label: -1 for label in self.peers}

        console.print(f"\n[bold yellow on blue] *** NÓ {self.node_label} VENCEU A ELEIÇÃO E AGORA É O Leader! *** [/bold yellow on blue]\n")
        threading.Thread(target=self._loop_heartbeat, daemon=True).start()

    # =========================================================================
    # LÓGICA DE REPLICAÇÃO (ENVIO gRPC)
    # =========================================================================

    def _loop_heartbeat(self):
        console.print(f"\n[bold blue][>] Thread de sincronização do Líder iniciada![/bold blue]")
        while self.state == RaftState.LEADER.value:
            for label, address in self.peers.items():
                threading.Thread(target=self._sincronizar_peer, args=(label, address), daemon=True).start()
            
            self._resetar_timeout()
            time.sleep(HEARTBEAT_INTERVAL)
            
        console.print(f"\n[yellow][!] O nó {self.node_label} deixou de ser Leader. Encerrando thread de sincronização.[/yellow]")

    def _sincronizar_peer(self, label, address):
        try:
            with grpc.insecure_channel(address) as channel:
                stub = raft_pb2_grpc.RaftServiceStub(channel)
                
                next_idx = self.nextIndex.get(label, len(self.log))
                prev_log_index = next_idx - 1
                prev_log_term = self.log[prev_log_index].term if prev_log_index >= 0 else 0
                
                # Converte os objetos LogEntry internos para as mensagens LogEntry do gRPC protobuf
                entradas_proto = [
                    raft_pb2.LogEntry(term=e.term, command=e.command) 
                    for e in self.log[next_idx:]
                ]
                
                request = raft_pb2.AppendEntriesRequest(
                    leader_term=self.term,
                    leader_id=self.node_label,
                    prev_log_index=prev_log_index,
                    prev_log_term=prev_log_term,
                    entries=entradas_proto,
                    leader_commit=self.commitIndex
                )
                
                resposta = stub.append_entries(request, timeout=PEER_HEARTBEAT_TIMEOUT)
                
                if resposta.term > self.term:
                    self.term = resposta.term
                    self.state = RaftState.FOLLOWER.value
                    self.votedFor = None
                    return

                if resposta.success:
                    if entradas_proto:
                        self.nextIndex[label] = next_idx + len(entradas_proto)
                        self.matchIndex[label] = self.nextIndex[label] - 1
                        console.print(f"[green][+] Nó {label} atualizado com sucesso até o log {self.matchIndex[label]}[/green]")
                        self._atualizar_commit_index() 
                else:
                    self.nextIndex[label] = max(0, next_idx - 1)
                    
        except grpc.RpcError:
            pass # Nó offline

    def _atualizar_commit_index(self):
        todos_indices = list(self.matchIndex.values())
        todos_indices.append(len(self.log) - 1)
        todos_indices.sort(reverse=True)
        maioria_idx = len(NODES_CONFIG) // 2
        N = todos_indices[maioria_idx]
        
        if N > self.commitIndex and N >= 0 and self.log[N].term == self.term:
            self.commitIndex = N
            console.print(f"[magenta][^] QUÓRUM ATINGIDO! Commit Index avançou para {self.commitIndex}[/magenta]")

    # =========================================================================
    # EXPOSIÇÃO RPC (MÉTODOS DO SERVIDOR gRPC)
    # =========================================================================

    def request_vote(self, request, context):
        """Implementa o recebimento de pedido de voto via gRPC."""
        self._resetar_timeout()
        
        if request.candidate_term < self.term:
            return raft_pb2.VoteReply(term=self.term, success=False)

        if request.candidate_term > self.term:
            self.term = request.candidate_term
            self.state = RaftState.FOLLOWER.value
            self.votedFor = None

        meu_ultimo_indice = len(self.log) - 1
        meu_ultimo_termo = self.log[meu_ultimo_indice].term if meu_ultimo_indice >= 0 else 0
        
        log_ok = (request.last_log_term > meu_ultimo_termo) or \
                 (request.last_log_term == meu_ultimo_termo and request.last_log_index >= meu_ultimo_indice)

        if (self.votedFor is None or self.votedFor == request.candidate_id) and log_ok:
            self.votedFor = request.candidate_id
            self._resetar_timeout()
            console.print(f"[cyan][V] Voto concedido ao Nó {request.candidate_id} para o termo {request.candidate_term}[/cyan]")
            return raft_pb2.VoteReply(term=self.term, success=True)
            
        return raft_pb2.VoteReply(term=self.term, success=False)

    def append_entries(self, request, context):
        """Implementa o recebimento de AppendEntries/Heartbeats via gRPC."""
        self._resetar_timeout()
        
        if request.leader_term >= self.term:
            self.term = request.leader_term
            self.state = RaftState.FOLLOWER.value

        if request.leader_term < self.term:
            return raft_pb2.AppendEntriesReply(term=self.term, success=False)
        
        qtd_entradas = len(request.entries)
        if qtd_entradas > 0:
            console.print(f"\n[cyan][<] AppendEntries recebido de {request.leader_id} | PrevLogIndex: {request.prev_log_index} | Novas Entradas: {qtd_entradas}[/cyan]")
        
        if request.prev_log_index >= 0:
            if request.prev_log_index >= len(self.log):
                return raft_pb2.AppendEntriesReply(term=self.term, success=False)
            if self.log[request.prev_log_index].term != request.prev_log_term:
                return raft_pb2.AppendEntriesReply(term=self.term, success=False)
        
        # Converte de volta de Protobuf para LogEntry local
        entradas_obj = [LogEntry(e.term, e.command) for e in request.entries]
        
        novas_entradas_iniciar_em = 0
        for i, entry in enumerate(entradas_obj):
            index = request.prev_log_index + 1 + i
            if index < len(self.log):
                if self.log[index].term != entry.term:
                    console.print(f"[bold red][!] Conflito no índice {index}! Removendo antigas...[/bold red]")
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

        if request.leader_commit > self.commitIndex:
            novo_commit = min(request.leader_commit, len(self.log) - 1)
            if novo_commit > self.commitIndex:
                console.print(f"[magenta][^] Commit Index avançou de {self.commitIndex} para {novo_commit}[/magenta]")
            self.commitIndex = novo_commit
            
        return raft_pb2.AppendEntriesReply(term=self.term, success=True)

    def receber_comando(self, request, context):
        """Recebe comando do Cliente via gRPC."""
        console.print(f"\n[cyan][>] Comando recebido do cliente: '{request.comando}'[/cyan]")
        if self.state != RaftState.LEADER.value:
            return raft_pb2.ComandoReply(resultado="Erro: Nó não é o líder atual.")
        
        nova_entrada = LogEntry(self.term, request.comando)
        self.log.append(nova_entrada)
        return raft_pb2.ComandoReply(resultado=f"Comando '{request.comando}' enfileirado com sucesso.")

    # =========================================================================
    # INTERFACE GRÁFICA (PERMANECE IGUAL)
    # =========================================================================

    def _calcular_cor_estado(self):
        if self.state == RaftState.FOLLOWER.value: return "green"
        elif self.state == RaftState.CANDIDATE.value: return "magenta"
        else: return "yellow"

    def _construir_descricao_progresso(self, decorrido):
        color = self._calcular_cor_estado()
        comandos_log = [entrada.command for entrada in self.log]
        linha1 = f"[{color}]Estado: {self.state:<9}[/{color}] | Termo: {self.term} | Timeout: {self.timeout:.1f}s"
        linha2 = f"Commit: {self.commitIndex} | Log: {comandos_log}"
        return f"{linha1}\n{linha2}"

    def iniciar_loop_principal(self):
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
                if self.state in (RaftState.FOLLOWER.value, RaftState.CANDIDATE.value):
                    if decorrido > self.timeout:
                        self._processar_timeout_eleicao()
                        progress.reset(task_id, total=self.timeout)
                elif self.state == RaftState.LEADER.value:
                    if decorrido > self.timeout:
                        progress.reset(task_id, total=self.timeout)
                        self._resetar_timeout()

# =============================================================================
# INICIALIZAÇÃO COM SERVIDOR gRPC
# =============================================================================

def _obter_escolha_usuario():
    console.print("\n[bold cyan]================================[/bold cyan]")
    console.print("[bold cyan]=   Inicialização do Nó Raft   =[/bold cyan]")
    for label, info in NODES_CONFIG.items():
        console.print(f"  [bold white][{label}][/bold white] - Porta {info['port']}")
    console.print("[bold cyan]================================[/bold cyan]\n")
    escolha = input("Escolha a letra do processo para iniciar (A, B, C ou D): ").upper()
    return escolha if escolha in NODES_CONFIG else None

def iniciar_servidor():
    escolha = _obter_escolha_usuario()
    if escolha is None:
        console.print("[bold red]Escolha inválida![/bold red]")
        return
        
    config = NODES_CONFIG[escolha]
    
    # Instancia a classe do seu Nó Raft
    node = RaftNode(escolha, config['id'])
    
    # Cria o servidor gRPC com um pool de threads para lidar com requisições concorrentes
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    raft_pb2_grpc.add_RaftServiceServicer_to_server(node, server)
    
    # Vincula o servidor à porta configurada
    server.add_insecure_port(f"[::]:{config['port']}")
    server.start()
    console.print(f"[green][NET] Servidor gRPC rodando na porta {config['port']}...[/green]")
    
    # Inicia a thread responsável pela UI e pelo relógio do Timeout Raft
    thread_loop = threading.Thread(target=node.iniciar_loop_principal, daemon=True)
    thread_loop.start()
    
    # Mantém a thread principal viva esperando o gRPC terminar
    server.wait_for_termination()

if __name__ == "__main__":
    iniciar_servidor()