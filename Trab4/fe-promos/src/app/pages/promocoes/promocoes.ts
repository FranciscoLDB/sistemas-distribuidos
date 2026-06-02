import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Promocao } from "../../components/promocao/promocao";
import { Filtro } from "../../compoents/filtro/filtro";

@Component({
  selector: 'app-promocoes',
  standalone: true,
  imports: [CommonModule, Promocao, Filtro], // 👈 Declare-o aqui
  templateUrl: './promocoes.html',
  styleUrl: './promocoes.css',
})
export class Promocoes {
  private LIMITE_HOT_DEAL = 5;

  // Lista mestre vinda do backend
  listaPromocoes = [
    {
      "id": 2,
      "nomeProduto": "Notebook",
      "descricao": "Dell i7 1tb de armazenamento SSD de alta performance",
      "precoOriginal": 4000.00,
      "precoPromocional": 2000.00,
      "categoria": { "id": 1, "nome": "ELETRONICOS" },
      "votos": 0,
      "dataCriacao": "2026-06-01T14:46:33.632176",
      "status": "NORMAL",
      "loja": { "id": 1, "nome": "TechZone", "imagemUrl": "https://placehold.co/100x100?text=TechZone" }
    },
    {
      "id": 1,
      "nomeProduto": "Moto Honda",
      "descricao": "XRE 190 nova, excelente para o dia a dia e viagens",
      "precoOriginal": 8000.00,
      "precoPromocional": 6500.00,
      "categoria": { "id": 2, "nome": "AUTOMOVEL" },
      "votos": 4,
      "status": "NORMAL",
      "loja": { "id": 2, "nome": "FastCar", "imagemUrl": "https://placehold.co/100x100?text=FastCar" }
    }
  ];

  // Lista que será de fato exibida no HTML
  promocoesFiltradas = [...this.listaPromocoes];

  // Estado atual dos filtros aplicados
  filtrosAtivos = {
    categoriaId: null as number | null,
    apenasDestaques: false
  };

  // Executa toda vez que o componente Filtro emite uma mudança
  aplicarFiltros(event: { categoriaId: number | null; apenasDestaques: boolean }) {
    this.filtrosAtivos.categoriaId = event.categoriaId;
    this.filtrosAtivos.apenasDestaques = event.apenasDestaques;
    this.recalcularListaExibida();
  }

  private recalcularListaExibida() {
    this.promocoesFiltradas = this.listaPromocoes.filter(promo => {
      // 1. Filtro por Categoria
      const atendeCategoria = this.filtrosAtivos.categoriaId === null || 
                               promo.categoria.id === this.filtrosAtivos.categoriaId;
      
      // 2. Filtro por Status Destaque
      const atendeDestaque = !this.filtrosAtivos.apenasDestaques || 
                             promo.status === 'DESTAQUE';

      return atendeCategoria && atendeDestaque;
    });
  }

  processarVoto(evento: { promoId: number; tipo: 'POSITIVO' | 'NEGATIVO' }) {
    const promocao = this.listaPromocoes.find(p => p.id === evento.promoId);
    
    if (promocao) {
      if (evento.tipo === 'POSITIVO') {
        promocao.votos++;
      } else {
        promocao.votos--;
      }

      if (promocao.votos >= this.LIMITE_HOT_DEAL) {
        promocao.status = 'DESTAQUE';
      } else {
        promocao.status = 'NORMAL';
      }

      // Força a atualização da tela respeitando os filtros que já estavam ligados
      this.recalcularListaExibida();
    }
  }
}