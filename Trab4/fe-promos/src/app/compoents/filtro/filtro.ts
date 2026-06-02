import { Component, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-filtro',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './filtro.html',
  styleUrl: './filtro.css',
})
export class Filtro {
  // Emite um objeto contendo a categoria selecionada (ou null para todas) e se deve filtrar por destaque
  @Output() filtroAlterado = new EventEmitter<{ categoriaId: number | null; apenasDestaques: boolean }>();

  // Mock de categorias baseado no banco de dados do seu Spring
  categorias = [
    { id: 1, nome: 'ELETRONICOS' },
    { id: 2, nome: 'AUTOMOVEL' },
    { id: 3, nome: 'CASA' },
    { id: 4, nome: 'LIVROS' }
  ];

  // Estado interno dos filtros
  categoriaSelecionadaId: number | null = null;
  apenasDestaques: boolean = false;

  selecionarCategoria(id: number | null) {
    this.categoriaSelecionadaId = id;
    this.notificarPai();
  }

  alternarDestaques() {
    this.apenasDestaques = !this.apenasDestaques;
    this.notificarPai();
  }

  private notificarPai() {
    this.filtroAlterado.emit({
      categoriaId: this.categoriaSelecionadaId,
      apenasDestaques: this.apenasDestaques
    });
  }
}