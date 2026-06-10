import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';

// Importa a versão Async estável
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideToastr } from 'ngx-toastr';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    
    // 💡 SOLUÇÃO: Passamos 'noop' como argumento! 
    // Isso desativa o motor de animação depreciado do Angular, limpando 100% os avisos do console.
    provideAnimationsAsync('noop'), 
    
    provideToastr({
      positionClass: 'toast-bottom-right',
      timeOut: 4000,
      progressBar: true,
      preventDuplicates: true,
    }),
  ]
};