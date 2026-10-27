import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { combineLatest } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { WorkspaceApi } from '../../core/api/workspace-api.service';
import { PageResource } from './page-resource';
import { RequestStateComponent } from './request-state.component';
@Component({
  selector: 'app-staff-page',
  imports: [RequestStateComponent, RouterLink],
  template: ` @if (back) {
      <a [routerLink]="back">← Back to {{ section }}</a>
    }
    <header class="workspace-heading">
      <p class="eyebrow">{{ section }}</p>
      <h1 tabindex="-1">{{ title }}</h1>
      <p>{{ description }}</p>
    </header>
    <section class="panel">
      <p class="eyebrow">READ-ONLY WORKSPACE</p>
      <app-request-state
        [loading]="resource.loading()"
        [error]="resource.error()"
        [status]="resource.status()"
        (retry)="load()"
      />
      @if (!resource.loading() && !resource.error()) {
        <p>No approved data source is available for this workspace.</p>
      }
    </section>
    <aside class="notice">
      <h2>What you can expect here</h2>
      <p>{{ expectation }}</p>
      <p>Downloads and actions that change client records are unavailable.</p>
    </aside>`,
})
export class StaffPageComponent {
  private readonly api = inject(WorkspaceApi);
  private readonly route = inject(ActivatedRoute);
  readonly resource = new PageResource<never>();
  title = '';
  section = '';
  description = '';
  expectation = '';
  back = '';
  endpoint = '';
  constructor() {
    combineLatest([this.route.data, this.route.paramMap])
      .pipe(takeUntilDestroyed())
      .subscribe(([data, params]) => {
        this.title = data['title'];
        this.section = data['section'];
        this.description = data['description'];
        this.expectation = data['expectation'];
        this.back = data['back'] ?? '';
        this.endpoint = data['endpoint'].replace(':id', encodeURIComponent(params.get('id') ?? ''));
        this.load();
      });
  }
  load() {
    this.resource.load(this.api.staffAvailability(this.endpoint));
  }
}
