// {
//     "id": 2,
//     "nomeProduto": "notebook",
//     "descricao": "dell i7 1tb",
//     "precoOriginal": 4000.00,
//     "precoPromocional": 2000.00,
//     "categoria": {
//       "id": 1,
//       "nome": "ELETRONICOS"
//     },
//     "votos": 0,
//     "dataCriacao": "2026-06-01T14:46:33.632176",
//     "status": "NORMAL",
//     "loja": {
//       "id": 1,
//       "nome": "TechZone",
//       "email": "fbruginski@alunos.utfpr.edu.br",
//       "imagemUrl": "http://drive.google.com/u/0/drive-viewer/AKGpihZknE-9-dUR5iDsF_4qUxbrH1q1qd1DSdDx4miICpLPN8YrMOSaWv5DHklkSrgiW6IDZOLq0UUlP2hyKyeW6sv301O5nrjsN8o=s1600-rw-v1?auditContext=forDisplay"
//     }
// }

export interface Categoria {
    id: number;
    nome: string;
}

export interface Loja {
    id: number;
    nome: string;
    email: string;
    imagemUrl: string;
}

export interface PromocaoModel {
    id: number;
    nomeProduto: string;
    descricao: string;
    precoOriginal: number;
    precoPromocional: number;
    categoria: Categoria;
    votos: number;
    dataCriacao: string;
    status: string;
    loja: Loja;
}