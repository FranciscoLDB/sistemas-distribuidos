import { Component, OnDestroy, signal, computed, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms'; // OBRIGATÓRIO adicionar para usar o [(ngModel)] no input de ID
import { Subscription } from 'rxjs';
import { Promocao } from "../../components/promocao/promocao";
import { PromocaoService } from "../../services/promocao-service";

@Component({
  selector: 'app-promocoes',
  standalone: true,
  imports: [CommonModule, FormsModule, Promocao], // Adicionado FormsModule aqui
  templateUrl: './promocoes.html',
  styleUrl: './promocoes.css',
})
export class Promocoes implements OnDestroy {
  private LIMITE_HOT_DEAL = 5;
  private sseSubscription!: Subscription;
  
  // Variáveis de controle de sessão do usuário
  usuarioId = signal<number | null>(null); // ID oficial consolidado
  inputIdForm = signal<number | null>(null); // Vinculado ao campo de texto da tela
  
  private REQUISITOR_MOCK = 0;
  private ASSINATURA_MOCK = "assinatura_digital_hash_aqui";

  listaPromocoes = signal<any[]>([]);
  listaCategorias = signal<any[]>([]);
  
  filtrosAtivos = signal({
    categoriaIds: [] as number[], 
    apenasDestaques: false
  });

  promocoesFiltradas = computed(() => {
    const lista = this.listaPromocoes();
    const filtros = this.filtrosAtivos();

    return lista.filter(promo => {
      const atendeCategoria = filtros.categoriaIds.length === 0 || 
                             (promo.categoria && filtros.categoriaIds.includes(promo.categoria.id));
      const atendeDestaque = !filtros.apenasDestaques || promo.status === 'DESTAQUE';
      return atendeCategoria && atendeDestaque;
    });
  });

  constructor(private promocaoService: PromocaoService, private ngZone: NgZone) {}

  conectarUsuario() {
    const idDigitado = this.inputIdForm();
    
    if (!idDigitado || idDigitado <= 0) {
      alert('Por favor, insira um ID de usuário válido antes de conectar.');
      return;
    }

    this.usuarioId.set(idDigitado);
    
    this.REQUISITOR_MOCK = idDigitado;

    console.log(`Usuário ${idDigitado} definido. Inicializando conexões com o servidor...`);

    this.carregarPromocoesDoServidor();
    this.conectarAoCanalSse();
  }

  private carregarPromocoesDoServidor() {
    this.promocaoService.listar().subscribe({
      next: (dados) => this.listaPromocoes.set(dados),
      error: (err) => console.error('Erro ao buscar promoções iniciais:', err)
    });
  }

  private conectarAoCanalSse() {
    this.sseSubscription = this.promocaoService
      .ouvirStreamNotificacoes(this.REQUISITOR_MOCK)
      .subscribe({
        next: (evento) => {
          this.ngZone.run(() => {
            console.log('Evento SSE recebido:', evento);
            
            if (evento.type === 'CATEGORIAS') {
              console.log('Categorias carregadas do banco via SSE:', evento.data);
              this.listaCategorias.set(evento.data);
            } 
            
            else if (evento.type === 'INTERESSES') {
              console.log('Interesses do usuário vindos do banco via SSE:', evento.data);
              this.sincronizarInteressesIniciais(evento.data);
            } 
            
            else if (evento.type === 'NOVA_PROMOCAO') {
              const novaPromocao = evento.data;
              console.log('Nova promoção recebida via SSE:', novaPromocao);
              this.listaPromocoes.update(lista => {
                const index = lista.findIndex(p => p.id === novaPromocao.id);
                return index !== -1 
                  ? (lista[index] = novaPromocao, [...lista]) 
                  : [novaPromocao, ...lista];
              });
            }

          });
        },
        error: (err) => console.error('Erro na conexão SSE streaming:', err)
      });
  }

  private sincronizarInteressesIniciais(interesses: string[]) {
    const categoriasDoBanco = this.listaCategorias();

    const idsMapeados = interesses.map(interesse => {
      // Se o interesse enviado pelo servidor já for o ID em formato String (Ex: "1")
      if (!isNaN(Number(interesse))) {
        return Number(interesse);
      }
      // Se enviado o Nome (Ex: "Eletrônicos"), busca o id correspondente no objeto Categoria
      const achado = categoriasDoBanco.find(c => c.nome.toLowerCase() === interesse.toLowerCase());
      return achado ? achado.id : null;
    }).filter(id => id !== null) as number[];

    // Marca os checkboxes na interface na mesma hora
    this.filtrosAtivos.update(f => ({ ...f, categoriaIds: idsMapeados }));
  }

  alternarFiltroCategoria(categoriaId: number, evento: Event) {
    const checkbox = evento.target as HTMLInputElement;
    const estaSelecionado = checkbox.checked;
    const idAtual = this.usuarioId();

    if (!idAtual) return; // Segurança caso não tenha usuário

    if (estaSelecionado) {
      this.promocaoService.cadastrarInteresse(
        idAtual, 
        [categoriaId],
        "REQUISITOR_MOCK",
        this.ASSINATURA_MOCK
      ).subscribe({
        next: (idAtualizados: number[]) => {
          console.log('Interesse cadastrado no banco, IDs atualizados:', idAtualizados);
          this.filtrosAtivos.update(f => ({ ...f, categoriaIds: idAtualizados }));
        },
        error: (err) => {
          console.error('Erro no POST interesse, aplicando local:', err);
          this.filtrosAtivos.update(f => ({ ...f, categoriaIds: [...f.categoriaIds, categoriaId] }));
        }
      });
    } else {
      this.promocaoService.removerInteresse(
        idAtual, 
        [categoriaId], 
        "REQUISITOR_MOCK", 
        this.ASSINATURA_MOCK
      ).subscribe({
        next: (idAtualizados: number[]) => {
          console.log('Interesse removido no banco, IDs atualizados:', idAtualizados);
          this.filtrosAtivos.update(f => ({ ...f, categoriaIds: idAtualizados }));
        },
        error: (err) => {
          console.error('Erro no POST remover interesse, aplicando local:', err);
          this.filtrosAtivos.update(f => ({ ...f, categoriaIds: f.categoriaIds.filter(id => id !== categoriaId) }));
        }
      });
    }
  }

  alternarFiltroDestaques(evento: Event) {
    const checkbox = evento.target as HTMLInputElement;
    this.filtrosAtivos.update(f => ({ ...f, apenasDestaques: checkbox.checked }));
  }

  processarVoto(evento: { idPromocao: number; votoRecebido: number }) {
  this.promocaoService.votar(evento.idPromocao, evento.votoRecebido, "REQUISITOR_MOCK", this.ASSINATURA_MOCK).subscribe({
    next: (resposta) => {
      // Como o backend respondeu OK, atualizamos o estado localmente
      this.listaPromocoes.update(lista => {
        const idx = lista.findIndex(p => p.id === evento.idPromocao);
        if (idx !== -1) {
          // Fazemos uma cópia do objeto para manter a imutabilidade
          const promocaoAtualizada = { ...lista[idx] };
          
          // Incrementa ou decrementa os votos localmente baseado no que foi enviado
          promocaoAtualizada.votos += evento.votoRecebido; 
          
          lista[idx] = promocaoAtualizada;
        }
        return [...lista];
      });
    },
    error: (err) => {
      console.error('Erro ao votar:', err);
      // Aqui você poderia exibir um alerta amigável pro usuário
    }
  });
}

  // Permite deslogar ou trocar de usuário para testar cenários diferentes
  desconectar() {
    if (this.sseSubscription) {
      this.sseSubscription.unsubscribe();
    }
    this.usuarioId.set(null);
    this.listaPromocoes.set([]);
    this.filtrosAtivos.set({ categoriaIds: [], apenasDestaques: false });
  }

  ngOnDestroy(): void {
    if (this.sseSubscription) this.sseSubscription.unsubscribe();
  }
}