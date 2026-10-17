import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timeout, takeUntil } from 'rxjs';
import {
  BankingApi,
  BankReference,
  CashMovement,
  MovementCommand,
} from '../../core/api/banking-api.service';
import { WorkspaceApi } from '../../core/api/workspace-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { safeReturnUrl } from '../../core/auth/workspace-policy';
interface PendingMovement {
  key: string;
  command: MovementCommand;
}
@Component({
  selector: 'app-banking-page',
  imports: [FormsModule, DatePipe, RouterLink],
  template: `
    <header class="workspace-heading">
      <p class="eyebrow">MY ACCOUNT</p>
      <h1 tabindex="-1">Banking & cash</h1>
      <p>
        No money moves through a real bank. Deposits and withdrawals change your internal USD
        account only.
      </p>
    </header>
    <a [routerLink]="returnTree">{{
      returnUrl.startsWith('/app/trade/') ? 'Return to your order' : 'Back to your account'
    }}</a>
    <p role="status" aria-live="polite">{{ message() }}</p>
    <section class="panel">
      <h2>Available USD: {{ available() }}</h2>
      <p>Posted: {{ posted() }} · Reserved for orders: {{ reserved() }}</p>
      <p>A completed sale credits this account. Withdrawal is a separate action.</p>
      <button type="button" (click)="load()" [disabled]="busy()">
        Refresh balances and accounts
      </button>
    </section>
    <section class="panel">
      <h2>Saved bank accounts</h2>
      <p>
        Format checked references, not verified bank ownership. Full numbers are never returned.
      </p>
      @for (bank of banks(); track bank.bankId) {
        <article class="bank-reference">
          <h3>{{ bank.nickname }} · {{ bank.maskedAccount }}</h3>
          <p>{{ bank.defaultAccount ? 'Default account' : '' }}</p>
          <div class="actions">
            <button type="button" (click)="edit(bank)" [disabled]="busy() || !!pending()">
              Edit</button
            ><button type="button" (click)="removing.set(bank)" [disabled]="busy() || !!pending()">
              Remove
            </button>
          </div>
        </article>
      }
      @if (!banks().length) {
        <p>No bank account saved. Add one below before buying or selling.</p>
      }
      @if (removing(); as bank) {
        <section class="notice">
          <p>
            Remove {{ bank.nickname }} {{ bank.maskedAccount }}? Past receipts remain available.
            Pending orders must finish first.
          </p>
          <button type="button" (click)="remove(bank)" [disabled]="busy()">Confirm removal</button
          ><button type="button" (click)="removing.set(null)">Keep account</button>
        </section>
      }
      <form (ngSubmit)="saveBank()">
        <fieldset [disabled]="busy() || !!pending()">
          <legend>{{ editing ? 'Edit bank reference' : 'Add bank reference' }}</legend>
          <label for="bank-nickname">Nickname</label
          ><input
            id="bank-nickname"
            name="nickname"
            [(ngModel)]="nickname"
            aria-describedby="bank-nickname-help"
            [attr.aria-invalid]="!!bankError('nickname')"
          />
          <p id="bank-nickname-help">
            {{
              bankError('nickname') ||
                'For example: Fictitious test account. Choose a unique name for each account.'
            }}
          </p>
          <label for="bank-routing">Routing number (9 digits)</label
          ><input
            id="bank-routing"
            name="routing"
            [(ngModel)]="routing"
            type="password"
            inputmode="numeric"
            autocomplete="off"
            aria-describedby="bank-routing-help"
            [attr.aria-invalid]="!!bankError('routing')"
          />
          <p id="bank-routing-help">
            {{
              bankError('routing') ||
                'Enter all nine digits, including leading zeroes. Use fictitious details for this internal demonstration.'
            }}
          </p>
          <label for="bank-account">Account number (4–17 digits)</label
          ><input
            id="bank-account"
            name="account"
            [(ngModel)]="account"
            type="password"
            inputmode="numeric"
            autocomplete="off"
            aria-describedby="bank-account-help"
            [attr.aria-invalid]="!!bankError('account')"
          />
          <p id="bank-account-help">
            {{
              bankError('account') ||
                'Enter the full account number, including leading zeroes. This project accepts 4–17 digits.'
            }}
          </p>
          @if (editing) {
            <p>
              Leave both number fields empty to keep the saved details. To replace them, enter both
              full numbers.
            </p>
          }
          <label
            ><input type="checkbox" name="defaultAccount" [(ngModel)]="defaultAccount" /> Use as
            default</label
          >
          <button type="submit">{{ busy() ? 'Saving…' : 'Save bank account' }}</button>
          @if (editing) {
            <button type="button" (click)="resetBank()">Cancel edit</button>
          }
        </fieldset>
      </form>
    </section>
    <section class="panel">
      <h2>Deposit or withdraw</h2>
      <p>
        USD 10,000 maximum per movement. Deposits are limited to USD 10,000 total per UTC day.
        Withdrawals use settled, available cash.
      </p>
      @if (pending(); as p) {
        <section class="notice">
          <h3>Checking your saved request</h3>
          <p>{{ p.command.direction }} {{ p.command.amount }} USD · Reference {{ p.key }}</p>
          <p>
            A missing response does not mean failure. Keep this reference until the outcome is
            known.
          </p>
          <button type="button" (click)="recover()" [disabled]="busy()">Check outcome</button
          ><button type="button" data-testid="retry-movement" (click)="send()" [disabled]="busy()">
            Retry same request
          </button>
        </section>
      } @else {
        <form (ngSubmit)="review()">
          <fieldset [disabled]="busy()">
            <legend>1. Choose movement</legend>
            <label for="movement-bank">Saved bank account</label
            ><select
              id="movement-bank"
              name="bank"
              [(ngModel)]="selectedBank"
              (ngModelChange)="reviewing.set(null)"
            >
              <option value="">Choose an account</option>
              @for (bank of banks(); track bank.bankId) {
                <option [value]="bank.bankId">{{ bank.nickname }} {{ bank.maskedAccount }}</option>
              }
            </select>
            <label for="movement-direction">Action</label
            ><select
              id="movement-direction"
              name="direction"
              [(ngModel)]="direction"
              (ngModelChange)="reviewing.set(null)"
            >
              <option value="DEPOSIT">Deposit</option>
              <option value="WITHDRAW">Withdraw</option>
            </select>
            <label for="movement-amount">Amount in USD</label
            ><input
              id="movement-amount"
              name="amount"
              [(ngModel)]="amount"
              (ngModelChange)="reviewing.set(null)"
              inputmode="decimal"
              type="text"
            />
            <button type="submit" [disabled]="!selectedBank || !validAmount()">
              Review movement
            </button>
          </fieldset>
        </form>
        @if (reviewing(); as r) {
          <section class="notice">
            <h3>2. Review and confirm</h3>
            <p>
              {{ r.direction === 'DEPOSIT' ? 'Add' : 'Withdraw' }} {{ r.amount }} USD using
              {{ bankLabel(r.bankId) }}.
            </p>
            @if (projectedAvailable(r); as estimate) {
              <p>Estimated available after movement: {{ estimate }} USD</p>
            } @else {
              <p>Balance estimate unavailable. Refresh your balances before confirming.</p>
            }
            <p>
              The server checks your current available funds and limits again when you confirm.
              Funds change only when the movement commits.
            </p>
            <p>This settles immediately in the internal ledger, with no real bank transfer.</p>
            <button type="button" (click)="confirm()" [disabled]="busy()">
              Confirm {{ r.direction.toLowerCase() }}</button
            ><button type="button" (click)="reviewing.set(null)">Back</button>
          </section>
        }
      }
      @if (receipt(); as r) {
        <section class="notice" aria-live="polite">
          <h3>{{ r.status }} · {{ r.direction }} {{ r.amount }} {{ r.currency }}</h3>
          <p>{{ r.bankSnapshot }}</p>
          <p>{{ r.reason || 'Committed to your internal account.' }}</p>
          <p>Receipt {{ r.movementId }}</p>
        </section>
      }
    </section>
    <section class="panel">
      <h2>Cash movement history</h2>
      @for (r of history(); track r.movementId) {
        <article>
          <p>{{ r.direction }} {{ r.amount }} USD · {{ r.status }}</p>
          <p>{{ r.bankSnapshot }} · {{ r.recordedAt | date: 'medium' : 'UTC' }} UTC</p>
          <p>{{ r.reason }}</p>
          <small>Receipt {{ r.movementId }}</small>
        </article>
      }
      @if (!history().length) {
        <p>No movements on this page.</p>
      }
      <div class="actions">
        <button type="button" (click)="historyPage(-1)" [disabled]="page === 0">Previous</button
        ><span>Page {{ page + 1 }}</span
        ><button type="button" (click)="historyPage(1)" [disabled]="history().length < 20">
          Next
        </button>
      </div>
    </section>
  `,
})
export class BankingPageComponent {
  private readonly api = inject(BankingApi);
  private readonly workspace = inject(WorkspaceApi);
  private readonly auth = inject(AuthService);
  private readonly destroy = inject(DestroyRef);
  readonly returnUrl = safeReturnUrl(
    inject(ActivatedRoute).snapshot.queryParamMap.get('returnTo'),
    ['CLIENT'],
  );
  readonly returnTree = inject(Router).parseUrl(this.returnUrl);
  readonly banks = signal<BankReference[]>([]);
  readonly history = signal<CashMovement[]>([]);
  readonly message = signal('');
  readonly busy = signal(false);
  readonly available = signal('—');
  readonly posted = signal('—');
  readonly reserved = signal('—');
  readonly removing = signal<BankReference | null>(null);
  readonly pending = signal<PendingMovement | null>(null);
  readonly reviewing = signal<MovementCommand | null>(null);
  readonly receipt = signal<CashMovement | null>(null);
  readonly bankAttempted = signal(false);
  editing?: BankReference;
  nickname = '';
  routing = '';
  account = '';
  defaultAccount = false;
  selectedBank = '';
  direction: 'DEPOSIT' | 'WITHDRAW' = 'DEPOSIT';
  amount = '';
  page = 0;

  constructor() {
    this.load();
    this.readPending();
    if (this.pending()) this.recover();
    this.auth.sessionChanged.pipe(takeUntilDestroyed()).subscribe(() => {
      this.pending.set(null);
      this.banks.set([]);
      this.history.set([]);
      this.resetBank();
      this.receipt.set(null);
      this.available.set('—');
      this.posted.set('—');
      this.reserved.set('—');
      this.reviewing.set(null);
      this.removing.set(null);
      this.selectedBank = '';
      this.amount = '';
      this.message.set('');
      this.busy.set(false);
    });
  }

  load() {
    this.api
      .banks()
      .pipe(takeUntil(this.auth.sessionChanged), takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (b) => {
          this.banks.set(b);
          if (!b.some((x) => x.bankId === this.selectedBank))
            this.selectedBank = b.find((x) => x.defaultAccount)?.bankId ?? b[0]?.bankId ?? '';
        },
        error: (e) => this.error(e),
      });
    this.workspace
      .portfolio()
      .pipe(takeUntil(this.auth.sessionChanged), takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (p) => {
          const b = p.balances?.find((x) => x.currency === 'USD');
          this.available.set(b?.available ?? 'Unavailable');
          this.posted.set(b?.posted ?? '—');
          this.reserved.set(b?.reserved ?? '—');
        },
        error: (e) => this.error(e),
      });
    this.loadHistory();
  }

  private loadHistory() {
    this.api
      .history(this.page)
      .pipe(takeUntil(this.auth.sessionChanged), takeUntilDestroyed(this.destroy))
      .subscribe({ next: (h) => this.history.set(h), error: (e) => this.error(e) });
  }

  historyPage(delta: number) {
    this.page += delta;
    this.loadHistory();
  }

  bankLabel(id: string) {
    const b = this.banks().find((b) => b.bankId === id);
    return b ? `${b.nickname} ${b.maskedAccount}` : 'saved account';
  }

  edit(bank: BankReference) {
    this.bankAttempted.set(false);
    this.editing = bank;
    this.nickname = bank.nickname;
    this.defaultAccount = bank.defaultAccount;
    this.routing = '';
    this.account = '';
  }

  resetBank() {
    this.bankAttempted.set(false);
    this.editing = undefined;
    this.nickname = '';
    this.routing = '';
    this.account = '';
    this.defaultAccount = false;
  }

  bankError(field: 'nickname' | 'routing' | 'account'): string {
    if (!this.bankAttempted() && !this[field]) return '';
    if (field === 'nickname')
      return /^[A-Za-z0-9 .'-]{2,50}$/.test(this.nickname.trim()) &&
        /[A-Za-z0-9]/.test(this.nickname)
        ? ''
        : 'Nickname: 2–50 letters, digits, spaces, periods, apostrophes or hyphens; include a letter or digit';
    if (this.editing && !this.routing && !this.account) return '';
    return field === 'routing'
      ? /^[0-9]{9}$/.test(this.routing)
        ? ''
        : 'Routing number must contain exactly 9 digits'
      : /^[0-9]{4,17}$/.test(this.account)
        ? ''
        : 'Account number must contain 4–17 digits; enter the full number when changing it';
  }

  saveBank() {
    if (this.busy() || this.pending()) return;
    this.bankAttempted.set(true);
    if (this.bankError('nickname') || this.bankError('routing') || this.bankError('account')) {
      this.message.set('Check the bank fields below. Your reference has not been saved.');
      return;
    }
    this.busy.set(true);
    const body = {
      nickname: this.nickname,
      defaultAccount: this.defaultAccount,
      version: this.editing?.version,
      ...(this.routing || this.account || !this.editing
        ? { routingNumber: this.routing, accountNumber: this.account }
        : {}),
    };
    this.api
      .save(body, this.editing?.bankId)
      .pipe(takeUntil(this.auth.sessionChanged), takeUntilDestroyed(this.destroy))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.resetBank();
          this.message.set('Bank reference saved.');
          this.load();
        },
        error: (e) => {
          this.routing = '';
          this.account = '';
          this.error(e);
        },
      });
  }

  remove(bank: BankReference) {
    if (this.busy()) return;
    this.busy.set(true);
    this.api
      .remove(bank)
      .pipe(takeUntil(this.auth.sessionChanged), takeUntilDestroyed(this.destroy))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.removing.set(null);
          this.message.set('Bank reference removed.');
          this.load();
        },
        error: (e) => this.error(e),
      });
  }

  validAmount() {
    return (
      /^[0-9]{1,5}(\.[0-9]{1,2})?$/.test(this.amount) &&
      /[1-9]/.test(this.amount) &&
      Number(this.amount) <= 10000
    );
  }

  projectedAvailable(command: MovementCommand): string | null {
    const cents = (value: string): bigint | null => {
      if (!/^[0-9]+(\.[0-9]{1,2})?$/.test(value)) return null;
      const [whole, fraction = ''] = value.split('.');
      return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
    };
    const available = cents(this.available()),
      amount = cents(command.amount);
    if (available === null || amount === null) return null;
    const result = available + (command.direction === 'DEPOSIT' ? amount : -amount),
      absolute = result < 0n ? -result : result;
    return `${result < 0n ? '-' : ''}${absolute / 100n}.${(absolute % 100n).toString().padStart(2, '0')}`;
  }

  review() {
    if (!this.validAmount() || !this.selectedBank || this.pending()) return;
    this.reviewing.set({
      bankId: this.selectedBank,
      direction: this.direction,
      amount: this.amount,
    });
    this.receipt.set(null);
  }

  confirm() {
    const command = this.reviewing();
    if (!command || this.busy() || this.pending()) return;
    const p = { key: crypto.randomUUID(), command: { ...command } };
    try {
      const key = this.storageKey();
      if (!key) throw Error();
      sessionStorage.setItem(key, JSON.stringify(p));
    } catch {
      this.message.set(
        'Enable session storage to preserve your request reference before submitting.',
      );
      return;
    }
    this.pending.set(p);
    this.reviewing.set(null);
    this.send();
  }

  send() {
    const p = this.pending();
    if (!p || this.busy()) return;
    this.busy.set(true);
    this.api
      .move(p.command, p.key)
      .pipe(timeout(15000), takeUntil(this.auth.sessionChanged), takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (r) => this.resolved(r),
        error: (e) => {
          this.error(e);
          if (e.status === 400 || e.status === 404) {
            this.clearPending();
            this.message.update(
              (m) => m + ' This request was not accepted. Correct the details and review again.',
            );
          } else {
            this.message.update(
              (m) => m + ' Your request is saved. Check its outcome or retry the same request.',
            );
          }
        },
      });
  }

  recover() {
    const p = this.pending();
    if (!p || this.busy()) return;
    this.busy.set(true);
    this.api
      .recover(p.key)
      .pipe(timeout(15000), takeUntil(this.auth.sessionChanged), takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (r) => this.resolved(r),
        error: () => {
          this.busy.set(false);
          this.message.set('Outcome is not confirmed. Check again or retry the same request.');
        },
      });
  }

  private resolved(r: CashMovement) {
    this.busy.set(false);
    this.receipt.set(r);
    this.clearPending();
    this.message.set('Movement outcome received.');
    this.load();
  }

  private clearPending() {
    this.pending.set(null);
    try {
      const key = this.storageKey();
      if (key) sessionStorage.removeItem(key);
    } catch {}
  }

  private storageKey() {
    const id = this.auth.identity()?.userId;
    return id == null ? null : `tadpoles.pending-movement.${id}`;
  }

  private readPending() {
    try {
      const key = this.storageKey();
      const raw = key ? sessionStorage.getItem(key) : null;
      if (!raw || raw.length > 2048) return;
      const p = JSON.parse(raw);
      if (
        /^[a-zA-Z0-9_-]{16,128}$/.test(p.key) &&
        typeof p.command?.bankId === 'string' &&
        ['DEPOSIT', 'WITHDRAW'].includes(p.command.direction) &&
        typeof p.command.amount === 'string'
      )
        this.pending.set(p);
    } catch {}
  }

  private error(e: { error?: { message?: string } }) {
    this.busy.set(false);
    this.message.set(e.error?.message ?? 'Unable to complete the request. Try again.');
  }
}
