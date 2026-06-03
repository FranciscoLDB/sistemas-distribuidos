import { Injectable, NgZone, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PromocaoModel } from '../models/promocaoModel';


@Injectable({ providedIn: 'root' })
export class PromocaoService {
  private apiUrl = 'http://localhost:8080/v1/promocao';

  constructor(private http: HttpClient, private zone: NgZone) {}

  // 1. GET - Listagem inicial de todas as promoções
  listar(): Observable<any[]> {
    return this.http.get<any[]>(this.apiUrl);
  }

  // 2. POST - Envio do voto para o backend (MS Gateway -> RabbitMQ)
  votar(promoId: number, tipo: 'POSITIVO' | 'NEGATIVO', requisitor: string, assinatura: string): Observable<any> {
    const headers = new HttpHeaders({
      'X-Requisitor': requisitor,
      'X-Assinatura': assinatura
    });

    const body = { promoId, tipo };
    return this.http.post(`${this.apiUrl}/votar`, body, { headers });
  }

  // 3. SSE - Inscrição no canal de streaming de eventos em tempo real
  ouvirStreamNotificacoes(consumidorId: string): Observable<any> {
    return new Observable(observer => {
      // Como o EventSource nativo não envia headers customizados facilmente na especificação,
      // passamos o identificador via query param ou na URL como o padrão do mercado para SSE.
      // Ajuste o endpoint do Spring para receber se necessário, ou envie fixo para testes.
      const eventSource = new EventSource(`${this.apiUrl}/stream?consumidorId=${consumidorId}`);

      eventSource.onopen = () => {
        console.log('Conexão SSE estabelecida');
      }

      // Escuta os eventos nomeados como "NOVA_PROMOCAO" vindos do seu Spring Boot
      eventSource.addEventListener('NOVA_PROMOCAO', (event: MessageEvent) => {
        this.zone.run(() => {
          const novaPromo = JSON.parse(event.data);
          observer.next(novaPromo);
        });
      });

      // Tratamento de erros no canal de comunicação
      eventSource.onerror = (error) => {
        this.zone.run(() => {
          observer.error(error);
        });
      };

      // Fecha a conexão caso o componente pare de ouvir (evita memory leak)
      return () => {
        eventSource.close();
      };
    });
  }
}