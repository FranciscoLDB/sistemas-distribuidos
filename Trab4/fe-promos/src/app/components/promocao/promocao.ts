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
  @Output() votoAlterado = new EventEmitter<{ idPromocao: number; votoRecebido:number }>();

  votar(voto: number) {
    this.votoAlterado.emit({ idPromocao: this.promo.id, votoRecebido: voto });
  }

  calcularDesconto(): number {
    if (!this.promo.precoOriginal || !this.promo.precoPromocional) return 0;
    const desconto = ((this.promo.precoOriginal - this.promo.precoPromocional) / this.promo.precoOriginal) * 100;
    return Math.round(desconto);
  }
}