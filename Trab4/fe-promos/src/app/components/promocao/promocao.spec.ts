import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Promocao } from './promocao';

describe('Promocao', () => {
  let component: Promocao;
  let fixture: ComponentFixture<Promocao>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Promocao],
    }).compileComponents();

    fixture = TestBed.createComponent(Promocao);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
