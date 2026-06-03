import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { Promocao } from "../../components/promocao/promocao";
import { Filtro } from "../../compoents/filtro/filtro";
import { PromocaoService } from "../../services/promocao-service";
import { PromocaoModel } from "../../models/promocaoModel";

export interface Toast {
  id: number;
  promocao: PromocaoModel;
}

let toastIdCounter = 0;
const TOAST_DURATION = 5000; // Duração do toast em milissegundos

@Component({
  selector: 'app-promocoes',
  standalone: true,
  imports: [CommonModule, Promocao, Filtro],
  templateUrl: './promocoes.html',
  styleUrl: './promocoes.css',
})
export class Promocoes implements OnInit, OnDestroy {
  private LIMITE_HOT_DEAL = 5;
  private sseSubscription!: Subscription;
  readonly toasts = [] as Toast[];
  
  private REQUISITOR_MOCK = "consumidor" + Math.floor(Math.random() * 1000); // Gera um ID de consumidor aleatório para testes
  private ASSINATURA_MOCK = "assinatura_digital_hash_aqui";

  listaPromocoes: any[] = [];
  promocoesFiltradas: any[] = [];

  filtrosAtivos = {
    categoriaId: null as number | null,
    apenasDestaques: false
  };

  // Injeção do service criado
  constructor(private promocaoService: PromocaoService) {}

  ngOnInit(): void {
    this.carregarPromocoesDoServidor();
    this.conectarAoCanalSse();
  }

  ngOnDestroy(): void {
    // Garante que o streaming será fechado se o usuário mudar de página
    if (this.sseSubscription) {
      this.sseSubscription.unsubscribe();
    }
  }

  // Busca inicial das promoções via REST comum
  private carregarPromocoesDoServidor() {
    this.promocaoService.listar().subscribe({
      next: (dados) => {
        this.listaPromocoes = dados;
        this.recalcularListaExibida();
      },
      error: (err) => console.error('Erro ao buscar promoções iniciais:', err)
    });
  }

  // Inicia a escuta SSE ativa em background
  private conectarAoCanalSse() {
    this.sseSubscription = this.promocaoService
      .ouvirStreamNotificacoes(this.REQUISITOR_MOCK)
      .subscribe({
        next: (novaPromocao) => {
          console.log('Nova promoção recebida em tempo real via SSE:', novaPromocao);
          this.exibirToast(novaPromocao);
          
          // Verifica se a promoção já existe na nossa lista atual (evita duplicar)
          const index = this.listaPromocoes.findIndex(p => p.id === novaPromocao.id);
          
          if (index !== -1) {
            // Atualiza o registro existente
            this.listaPromocoes[index] = novaPromocao;
          } else {
            // Insere a nova promoção no início da lista da interface gráfica
            this.listaPromocoes.unshift(novaPromocao);
          }
          
          this.recalcularListaExibida();
        },
        error: (err) => console.error('Erro na conexão SSE streaming:', err)
      });
  }

  private exibirToast(promocao: any) {
    const toast: Toast = {
      id: ++toastIdCounter,
      promocao
    };
    this.toasts.unshift(toast);
    setTimeout(() => 
      { this.toasts.filter(t => t.id !== toast.id); }, 
      TOAST_DURATION
    );
  }

  aplicarFiltros(event: { categoriaId: number | null; apenasDestaques: boolean }) {
    this.filtrosAtivos.categoriaId = event.categoriaId;
    this.filtrosAtivos.apenasDestaques = event.apenasDestaques;
    this.recalcularListaExibida();
  }

  private recalcularListaExibida() {
    this.promocoesFiltradas = this.listaPromocoes.filter(promo => {
      const atendeCategoria = this.filtrosAtivos.categoriaId === null || 
                               promo.categoria.id === this.filtrosAtivos.categoriaId;
      
      const atendeDestaque = !this.filtrosAtivos.apenasDestaques || 
                             promo.status === 'DESTAQUE';

      return atendeCategoria && atendeDestaque;
    });
  }

  // Atualizado para disparar a requisição POST REST real para o seu Backend Spring Boot
  processarVoto(evento: { promoId: number; tipo: 'POSITIVO' | 'NEGATIVO' }) {
    this.promocaoService.votar(
      evento.promoId, 
      evento.tipo, 
      this.REQUISITOR_MOCK, 
      this.ASSINATURA_MOCK
    ).subscribe({
      next: (promocaoAtualizada) => {
        // O backend retorna o estado atualizado do objeto promoção
        const index = this.listaPromocoes.findIndex(p => p.id === evento.promoId);
        if (index !== -1) {
          this.listaPromocoes[index] = promocaoAtualizada;
          this.recalcularListaExibida();
        }
      },
      error: (err) => {
        console.error('Falha ao computar voto no backend:', err);
        
        // Fallback Local defensivo: se sua API REST ainda não estiver de pé,
        // ele computa em tela para você não travar os seus testes de layout frontend:
        const promocao = this.listaPromocoes.find(p => p.id === evento.promoId);
        if (promocao) {
          evento.tipo === 'POSITIVO' ? promocao.votos++ : promocao.votos--;
          promocao.status = promocao.votos >= this.LIMITE_HOT_DEAL ? 'DESTAQUE' : 'NORMAL';
          this.recalcularListaExibida();
        }
      }
    });
  }
}