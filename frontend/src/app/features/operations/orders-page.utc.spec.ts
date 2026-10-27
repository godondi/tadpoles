import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { OperationsApi } from '../../core/api/operations-api.service';
import { CatalogRefreshService } from '../../core/api/catalog-refresh.service';
import { OperationsOrdersComponent } from './orders-page.component';

describe('Operations UTC filter integration', () => {
  afterEach(() => TestBed.resetTestingModule());
  it('preserves seconds, interprets labels as UTC and refuses impossible dates without sending a request', () => {
    const api = {
      context: signal({ contextId: 'ops' }),
      changed: new Subject<void>(),
      orders: vi.fn(() => of({ items: [], hasMore: false })),
      policy: () => of({ configured: false, purposes: [] }),
      contextMessage: signal(''),
    };
    TestBed.configureTestingModule({
      imports: [OperationsOrdersComponent],
      providers: [
        provideRouter([]),
        { provide: OperationsApi, useValue: api },
        { provide: CatalogRefreshService, useValue: { watch: () => new Subject<void>() } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { data: {}, queryParamMap: convertToParamMap({}) } },
        },
      ],
    });
    const fixture = TestBed.createComponent(OperationsOrdersComponent);
    const component = fixture.componentInstance;
    api.orders.mockClear();
    component.from = '2026-03-08T02:30:15';
    component.to = '2026-03-09T02:30:15';
    component.search();
    expect(api.orders).toHaveBeenLastCalledWith(
      expect.objectContaining({
        from: '2026-03-08T02:30:15.000Z',
        to: '2026-03-09T02:30:15.000Z',
      }),
    );
    api.orders.mockClear();
    component.from = '2026-02-29T12:00';
    component.search();
    expect(api.orders).not.toHaveBeenCalled();
    expect(component.validation()).toContain('valid UTC');
  });
});
