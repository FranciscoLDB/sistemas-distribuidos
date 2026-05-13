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
        # --- Configurações Básicas do Nó ---
        self.node_label = node_label
        self.node_id = node_id
        self.state = RaftState.FOLLOWER.value
        self.timeout = random.uniform(ELECTION_TIMEOUT_MIN, ELECTION_TIMEOUT_MAX)
        self.last_heartbeat = time.time()
        self.peers = {}  # URIs dos pares
        self.uri = f"PYRO:{node_id}@localhost:{NODES_CONFIG[node_label]['port']}"

        # --- Estado Persistente (Raft) ---
        self.term = 0
        self.votedFor = None
        self.log = []

        # --- Estado Volátil Geral (Raft) ---
        self.commitIndex = 0 # Começa em 0 indicando que nenhum índice foi comitado
        self.lastApplied = 0

        # --- Estado Volátil do Líder (Raft) ---
        self.nextIndex = {}
        self.matchIndex = {}
        
        self._exibir_inicializacao()
        self._configurar_conexoes()


    # =========================================================================
    # BLOCO 1: MÉTODOS DE CONFIGURAÇÃO E UTILITÁRIOS INTERNOS
    # =========================================================================
    
    def _exibir_inicializacao(self):
        """Exibe mensagem de inicialização formatada."""
        console.print("[bold blue]=======================================================[/bold blue]")
        console.print(f"[bold cyan]>> Nó {self.node_label}[/bold cyan] (ID:{self.node_id}) inicializado como [bold green]Follower[/bold green].")
        console.print(f"[T] Timeout de eleição: [yellow]{self.timeout:.2f}[/yellow] segundos.")
        console.print(f"[@] URI do nó: [white]{self.uri}[/white]")
        console.print("[bold blue]=======================================================[/bold blue]\n")

    def _configurar_conexoes(self):
        """Salva as URIs de todos os outros nós do cluster."""
        for label, info in NODES_CONFIG.items():
            if label != self.node_label:
                uri = f"PYRO:{info['id']}@localhost:{info['port']}"
                self.peers[label] = uri

    def _resetar_timeout(self):
        """Reseta o relógio interno que dispara a eleição."""
        self.last_heartbeat = time.time()


    # =========================================================================
    # BLOCO 2: LÓGICA DE ELEIÇÃO (O QUE O CANDIDATO FAZ)
    # =========================================================================

    def _processar_timeout_eleicao(self):
        """É chamado quando o tempo do Follower esgota. Transforma o nó em Candidato."""
        console.print(f"\n[bold red][!] TIMEOUT ATINGIDO![/bold red]")
        self._iniciar_eleicao()

    def _iniciar_eleicao(self):
        """Inicia uma eleição: aumenta o termo e pede votos para todos."""
        self._resetar_timeout()
        self.term += 1
        console.print(f"\n[bold magenta][*] Iniciando eleição para termo {self.term}...[/bold magenta]")
        
        votos_recebidos = self._contar_votos()
        self._verificar_vitoria_eleicao(votos_recebidos)

    def _contar_votos(self):
        """Envia pedidos de voto para os outros nós e conta quantos disseram sim."""
        votos_recebidos = 1  # Já conta o próprio voto
        
        for label, uri in self.peers.items():
            if self._solicitar_voto_de_peer(label, uri):
                votos_recebidos += 1
        
        return votos_recebidos

    def _solicitar_voto_de_peer(self, label, uri):
        """Comunica-se com um par específico pedindo voto via rede."""
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

    def _verificar_vitoria_eleicao(self, votos_recebidos):
        """Avalia se atingiu a maioria absoluta do cluster (Quórum)."""
        maioria = len(NODES_CONFIG) // 2
        
        if votos_recebidos > maioria:
            self._tornar_se_lider()
        else:
            console.print(f"[yellow][-] Votos insuficientes ({votos_recebidos}). Retornando a Follower.[/yellow]\n")
            self.state = RaftState.FOLLOWER.value

    def _tornar_se_lider(self):
        """Muda o estado para Líder, inicializa variáveis de replicação e inicia os heartbeats."""
        self.state = RaftState.LEADER.value
        
        # Inicializa variáveis de acompanhamento do log para cada seguidor (§5.3)
        self.nextIndex = {label: len(self.log) for label in self.peers}
        self.matchIndex = {label: -1 for label in self.peers}

        console.print(f"\n[bold yellow on blue] *** NÓ {self.node_label} VENCEU A ELEIÇÃO E AGORA É O Leader! *** [/bold yellow on blue]\n")
        
        # Inicia a thread que vai disparar os heartbeats para os seguidores
        threading.Thread(target=self._loop_heartbeat, daemon=True).start()
        self._registrar_no_nameserver()


    # =========================================================================
    # BLOCO 3: LÓGICA DE REPLICAÇÃO (O QUE O LÍDER FAZ)
    # =========================================================================

    def _registrar_no_nameserver(self):
        """Anuncia para a rede (Name Server) quem é o líder atual, para o Cliente achar."""
        try:
            ns = Pyro5.api.locate_ns()
            ns.register("raft.leader", self.uri)
            console.print("[green][NET] Leader registrado no Name Server (pyro5-ns) com sucesso![/green]")
        except Exception:
            console.print("[grey]Aviso: Name Server não encontrado na rede. Ignorando registro...[/grey]")

    def _loop_heartbeat(self):
        """Fica rodando em background enviando mensagens de vida e replicação continuamente."""
        console.print(f"\n[bold blue][>] Thread de sincronização do Líder iniciada![/bold blue]")
        
        while self.state == RaftState.LEADER.value:
            for label, uri in self.peers.items():
                # Envia para cada nó em uma mini-thread separada para não travar o líder
                threading.Thread(target=self._sincronizar_peer, args=(label, uri), daemon=True).start()
            
            self._resetar_timeout()
            time.sleep(HEARTBEAT_INTERVAL)
            
        console.print(f"\n[yellow][!] O nó {self.node_label} deixou de ser Leader. Encerrando thread de sincronização.[/yellow]")

    def _sincronizar_peer(self, label, uri):
        """Leva os logs pendentes para o seguidor (ou apenas manda heartbeat se estiver atualizado)."""
        try:
            with Pyro5.api.Proxy(uri) as proxy:
                proxy._pyroTimeout = PEER_HEARTBEAT_TIMEOUT
                
                # 1. Pega o nextIndex que o líder acha que este peer tem
                next_idx = self.nextIndex.get(label, len(self.log))
                
                # 2. Calcula os parâmetros de consistência
                prev_log_index = next_idx - 1
                prev_log_term = self.log[prev_log_index].term if prev_log_index >= 0 else 0
                
                # 3. Empacota as entradas a partir do next_idx
                entradas_para_enviar = [{"term": e.term, "command": e.command} for e in self.log[next_idx:]]
                
                # 4. Dispara a requisição RPC para o seguidor
                sucesso = proxy.append_entries(
                    self.term, self.node_label, prev_log_index, prev_log_term, 
                    entradas_para_enviar, self.commitIndex
                )
                
                # 5. Avalia se o seguidor aceitou os dados
                if sucesso:
                    if entradas_para_enviar:
                        # Se aceitou, atualiza o mapa de progresso deste nó
                        self.nextIndex[label] = next_idx + len(entradas_para_enviar)
                        self.matchIndex[label] = self.nextIndex[label] - 1
                        console.print(f"[green][+] Nó {label} atualizado com sucesso até o log {self.matchIndex[label]}[/green]")
                        
                        # Verifica se pode commitar
                        self._atualizar_commit_index() 
                else:
                    # Se rejeitou, volta o nextIndex um passo para trás para sincronizar depois
                    novo_next_idx = max(0, next_idx - 1)
                    self.nextIndex[label] = novo_next_idx
                    
        except Exception:
            # Nó offline. Ignora silenciosamente.
            pass

    def _atualizar_commit_index(self):
        """Avalia se a maioria dos nós já gravou as mensagens, se sim, avança o Commit."""
        # Pega todos os índices de progresso (matchIndex) + o log local do Líder
        todos_indices = list(self.matchIndex.values())
        todos_indices.append(len(self.log) - 1)
        
        # Ordena de forma decrescente para achar a mediana
        todos_indices.sort(reverse=True)
        
        # Pega a mediana (que representa a Maioria Absoluta)
        maioria_idx = len(NODES_CONFIG) // 2
        N = todos_indices[maioria_idx]
        
        # Só comita se o índice avançou e pertence ao termo atual do líder
        if N > self.commitIndex and N >= 0 and self.log[N].term == self.term:
            self.commitIndex = N
            console.print(f"[magenta][^] QUÓRUM ATINGIDO! Commit Index avançou para {self.commitIndex}[/magenta]")


    # =========================================================================
    # BLOCO 4: EXPOSIÇÃO RPC (O QUE VEM DE FORA PELA REDE É RECEBIDO AQUI)
    # =========================================================================

    @Pyro5.api.expose
    def request_vote(self, candidate_term, candidate_id, last_log_index, last_log_term):
        """
        Processa requisição de voto vinda de outro nó candidato.
        Implementa a seção §5.2 e §5.4 do paper do Raft.
        """
        
        # 1. Se o termo do candidato é menor que o meu, recuso na hora.
        if candidate_term < self.term:
            return self.term, False

        # Se o termo recebido é maior, eu atualizo meu termo e viro seguidor
        if candidate_term > self.term:
            self.term = candidate_term
            self.state = RaftState.FOLLOWER.value
            self.votedFor = None # Reseta o voto para o novo termo

        # 2. Verificação de Segurança do Log (§5.4.1)
        # Um log é considerado mais atualizado se:
        # a) O último termo do log do candidato for maior que o meu.
        # b) Os termos forem iguais, mas o candidato tiver um log maior ou igual ao meu.
        
        meu_ultimo_indice = len(self.log) - 1
        meu_ultimo_termo = self.log[meu_ultimo_indice].term if meu_ultimo_indice >= 0 else 0
        
        log_ok = (last_log_term > meu_ultimo_termo) or \
                (last_log_term == meu_ultimo_termo and last_log_index >= meu_ultimo_indice)

        # 3. Decisão de Voto
        # Só voto se eu ainda não votei em ninguém (votedFor is None ou o próprio candidato)
        # E se o log do candidato for confiável (log_ok).
        if (self.votedFor is None or self.votedFor == candidate_id) and log_ok:
            self.votedFor = candidate_id
            self._resetar_timeout() # Reseta o timer de eleição pois reconheci um candidato válido
            
            console.print(f"[cyan][V] Voto concedido ao Nó {candidate_id} para o termo {candidate_term}[/cyan]")
            return self.term, True
    
        # Caso contrário, recuso o voto
        return self.term, False

    @Pyro5.api.expose
    def request_vote(self, candidate_term):
        """Processa requisição de voto vinda de outro nó candidato."""
        if candidate_term > self.term:
            console.print(f"[cyan][V] Concedendo voto para termo {candidate_term}.[/cyan]")
            self.term = candidate_term
            self.state = RaftState.FOLLOWER.value
            self._resetar_timeout()
            return True
        return False

    @Pyro5.api.expose
    def append_entries(self, term, leaderId, prevLogIndex, prevLogTerm, entries, leaderCommit):
        """Processa heartbeats e dados recebidos do líder atual."""
        self._resetar_timeout()
        if term >= self.term:
            self.term = term
            self.state = RaftState.FOLLOWER.value

        qtd_entradas = len(entries)
        if qtd_entradas > 0:
            console.print(f"\n[cyan][<] AppendEntries recebido de {leaderId} | PrevLogIndex: {prevLogIndex} | Novas Entradas: {qtd_entradas}[/cyan]")
        
        # 1. Rejeita se o termo do líder for antigo
        if term < self.term:
            return False
        
        # 2. Verifica buracos ou inconsistências no log
        if prevLogIndex >= 0:
            if prevLogIndex >= len(self.log):
                if qtd_entradas > 0: console.print(f"[yellow][!] Rejeitado: Faltam entradas anteriores ao índice {prevLogIndex}.[/yellow]")
                return False
            if self.log[prevLogIndex].term != prevLogTerm:
                if qtd_entradas > 0: console.print(f"[yellow][!] Rejeitado: O termo no índice {prevLogIndex} está em conflito.[/yellow]")
                return False
        
        entradas_obj = [LogEntry(e["term"], e["command"]) if isinstance(e, dict) else e for e in entries]
        
        # 3 e 4. Limpa conflitos e anexa novas entradas
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

        # 5. Atualiza o próprio Commit Index caso o Líder mande
        if leaderCommit > self.commitIndex:
            novo_commit = min(leaderCommit, len(self.log) - 1)
            if novo_commit > self.commitIndex:
                console.print(f"[magenta][^] Commit Index avançou de {self.commitIndex} para {novo_commit}[/magenta]")
            self.commitIndex = novo_commit
            
        return True

    @Pyro5.api.expose
    def receber_comando(self, comando):
        """Recebe comando do Cliente (só funciona se for o Líder)."""
        console.print(f"\n[cyan][>] Comando recebido do cliente: '{comando}'[/cyan]")
        
        if self.state != RaftState.LEADER.value:
            return "Erro: Nó não é o líder atual."
        
        nova_entrada = LogEntry(self.term, comando)
        self.log.append(nova_entrada)
        
        return f"Comando enfileirado com sucesso no termo {self.term}. Aguardando Quórum em background..."


    # =========================================================================
    # BLOCO 5: INTERFACE GRÁFICA (UI) E LOOP PRINCIPAL
    # =========================================================================

    def _calcular_cor_estado(self):
        """Retorna a cor da barra baseada no estado atual (Verde=Follower, Magenta=Candidate, Amarelo=Leader)."""
        if self.state == RaftState.FOLLOWER.value:
            return "green"
        elif self.state == RaftState.CANDIDATE.value:
            return "magenta"
        else:  
            return "yellow"

    def _construir_descricao_progresso(self, decorrido):
        """Monta o texto visual dinâmico que aparece no terminal."""
        color = self._calcular_cor_estado()
        comandos_log = [entrada.command for entrada in self.log]
        linha1 = f"[{color}]Estado: {self.state:<9}[/{color}] | Termo: {self.term} | Timeout: {self.timeout:.1f}s"
        linha2 = f"Commit: {self.commitIndex} | Log: {comandos_log}"
        return f"{linha1}\n{linha2}"

    def iniciar_loop_principal(self):
        """O Loop central de vida do nó (mantém o terminal e o timer rodando)."""
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
                        self._resetar_timeout()


# =============================================================================
# FUNÇÕES EXTERNAS DE INICIALIZAÇÃO
# =============================================================================

def _exibir_menu_inicial():
    console.print("\n[bold cyan]================================[/bold cyan]")
    console.print("[bold cyan]=   Inicialização do Nó Raft   =[/bold cyan]")
    for label, info in NODES_CONFIG.items():
        console.print(f"  [bold white][{label}][/bold white] - Porta {info['port']}")
    console.print("[bold cyan]================================[/bold cyan]\n")

def _obter_escolha_usuario():
    _exibir_menu_inicial()
    escolha = input("Escolha a letra do processo para iniciar (A, B, C ou D): ").upper()
    
    if escolha not in NODES_CONFIG:
        console.print("[bold red]Escolha inválida! Saindo...[/bold red]")
        return None
    return escolha

def _iniciar_node_com_daemon(escolha):
    config = NODES_CONFIG[escolha]
    daemon = Pyro5.api.Daemon(port=config['port'], host="localhost")
    
    node = RaftNode(escolha, config['id'])
    daemon.register(node, objectId=config['id'])
    
    # Inicia a thread responsável pelo loop principal do nó
    thread_loop = threading.Thread(target=node.iniciar_loop_principal, daemon=True)
    thread_loop.start()
    
    return daemon

def iniciar_servidor():
    escolha = _obter_escolha_usuario()
    if escolha is None:
        return
    daemon = _iniciar_node_com_daemon(escolha)
    daemon.requestLoop()

if __name__ == "__main__":
    iniciar_servidor()