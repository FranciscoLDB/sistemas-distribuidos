import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './header.html',
  styleUrl: './header.css',
})
export class Header {
  // Simulação de estado de login. Quando integrar com o Spring, esses dados virão de um Service.
  usuarioLogado: { nome: string; tipo: 'CONSUMIDOR' | 'LOJA' } | null = null;
  
  // Controle do menu/caixa de login abaixo do botão Conta
  exibirCaixaLogin = false;
  
  // Dados do formulário de login rápido
  loginData = {
    username: '',
    password: ''
  };

  // Termo da barra de pesquisa de promoções
  termoPesquisa = '';

  alternarCaixaLogin() {
    this.exibirCaixaLogin = !this.exibirCaixaLogin;
  }

  efetuarLoginRapido() {
    if (this.loginData.username && this.loginData.password) {
      // Simulação: se o nome contiver "loja", loga como loja (para testar o comportamento do seu trabalho)
      const tipo = this.loginData.username.toLowerCase().includes('loja') ? 'LOJA' : 'CONSUMIDOR';
      
      this.usuarioLogado = {
        nome: this.loginData.username,
        tipo: tipo
      };
      
      // Limpa o formulário e fecha a caixinha
      this.loginData = { username: '', password: '' };
      this.exibirCaixaLogin = false;
    }
  }

  logout() {
    this.usuarioLogado = null;
  }

  buscar() {
    console.log('Buscando promoções por:', this.termoPesquisa);
    // Aqui você disparará a busca para filtrar as promoções na tela principal
  }
}