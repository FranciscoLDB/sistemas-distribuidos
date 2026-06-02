import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-promocao',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './promocao.html',
  styleUrl: './promocao.css',
})
export class Promocao {
  @Input({ required: true }) promo!: any;
  @Output() votoAlterado = new EventEmitter<{ promoId: number; tipo: 'POSITIVO' | 'NEGATIVO' }>();

  votar(tipo: 'POSITIVO' | 'NEGATIVO') {
    this.votoAlterado.emit({ promoId: this.promo.id, tipo });
  }

  calcularDesconto(): number {
    if (!this.promo.precoOriginal || !this.promo.precoPromocional) return 0;
    const desconto = ((this.promo.precoOriginal - this.promo.precoPromocional) / this.promo.precoOriginal) * 100;
    return Math.round(desconto);
  }
}