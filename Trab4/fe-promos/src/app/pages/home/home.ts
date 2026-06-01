import { Component } from '@angular/core';
import { Header } from "../../components/header/header";
import { Footer } from "../../components/footer/footer";
import { Promocoes } from "../promocoes/promocoes";

@Component({
  selector: 'app-home',
  imports: [Header, Footer, Promocoes],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {}
