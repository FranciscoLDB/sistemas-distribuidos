import { Injectable, NgZone, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PromocaoModel } from '../models/promocaoModel';

@Injectable({ providedIn: 'root' })
export class PromocaoService {
  private apiUrl = 'http://localhost:8080/v1';

  constructor(private http: HttpClient, private zone: NgZone) {}

  // 1. GET - Listagem inicial de todas as promoções
  listar(): Observable<any[]> {
    return this.http.get<any[]>(this.apiUrl + '/promocao');
  }

  // 2. POST - Envio do voto para o backend (MS Gateway -> RabbitMQ)
  votar(idPromocao: number, votoRecebido: number, requisitor: string, assinatura: string): Observable<any> {
    const headers = new HttpHeaders({
      'X-Requisitor': requisitor,
      'X-Assinatura': assinatura
    });

    const body = { idPromocao, votoRecebido };
    console.log('Enviando voto para o backend:', body);
    return this.http.post(`${this.apiUrl}/promocao/votar`, body, { headers });
  }

  // 3. SSE - Inscrição no canal de streaming de eventos em tempo real
  ouvirStreamNotificacoes(consumidorId: number): Observable<any> {
    return new Observable(observer => {
      const eventSource = new EventSource(`${this.apiUrl}/promocao/stream?consumidorId=${consumidorId}`);

      eventSource.onopen = () => { console.log('Conexão SSE estabelecida'); }

      // Escuta os eventos nomeados como "NOVA_PROMOCAO" vindos do seu Spring Boot
      eventSource.addEventListener('NOVA_PROMOCAO', (event: MessageEvent) => {
        this.zone.run(() => {
          const novaPromo = JSON.parse(event.data);
          observer.next({ type: 'NOVA_PROMOCAO', data: novaPromo });
        });
      });

      // Escuta o evento de CATEGORIAS do seu try/catch Java
      eventSource.addEventListener('CATEGORIAS', (event: MessageEvent) => {
        this.zone.run(() => {
          const categorias = JSON.parse(event.data);
          observer.next({ type: 'CATEGORIAS', data: categorias });
        });
      });

      // Escuta o evento de INTERESSES do seu try/catch Java
      eventSource.addEventListener('INTERESSES', (event: MessageEvent) => {
        this.zone.run(() => {
          const interesses = JSON.parse(event.data);
          observer.next({ type: 'INTERESSES', data: interesses });
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

  cadastrarInteresse(usuarioId: number, categoriaIds: number[], requisitor: string, assinatura: string): Observable<any> {
    const headers = new HttpHeaders({
      'X-Requisitor': requisitor,
      'X-Assinatura': assinatura
    });
    const body = { usuarioId, categoriasInteresse: categoriaIds };
    return this.http.post(`${this.apiUrl}/user/interesse`, body, { headers });
  }

  removerInteresse(usuarioId: number, categoriaIds: number[], requisitor: string, assinatura: string): Observable<any> {
    const headers = new HttpHeaders({
      'X-Requisitor': requisitor,
      'X-Assinatura': assinatura
    });
    const options = {
      headers: headers,
      body: { usuarioId, categoriasInteresse: categoriaIds } // O DELETE com corpo exige essa estrutura no Angular
    };
    return this.http.delete(`${this.apiUrl}/user/interesse`, options);
  }
}