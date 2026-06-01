import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Promocoes } from './promocoes';

describe('Promocoes', () => {
  let component: Promocoes;
  let fixture: ComponentFixture<Promocoes>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Promocoes],
    }).compileComponents();

    fixture = TestBed.createComponent(Promocoes);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
