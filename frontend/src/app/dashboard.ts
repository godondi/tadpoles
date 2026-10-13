import { Component, signal, ViewChild, ElementRef, AfterViewInit, effect, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Chart, ChartConfiguration, CategoryScale, LinearScale, PointElement, LineElement, LineController, DoughnutController, Title, Tooltip, Legend, ArcElement, Filler } from 'chart.js';

Chart.register(CategoryScale, LinearScale, PointElement, LineElement, LineController, DoughnutController, Title, Tooltip, Legend, ArcElement, Filler);

interface Holding {
  symbol: string;
  name: string;
  shares: number;
  price: number;
  value: number;
  percent: number;
}

interface AccountHistory {
  date: string;
  value: number;
}

interface OrderForm {
  symbol: string;
  quantity: string;
  orderType: string;
  action: 'buy' | 'sell';
}

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.html',
  styleUrl: './app.css',
  imports: [FormsModule],
  host: {
    'role': 'main',
  },
})
export class DashboardComponent implements AfterViewInit {
  @ViewChild('lineCanvas', { static: false }) lineCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('pieCanvas', { static: false }) pieCanvas!: ElementRef<HTMLCanvasElement>;

  private readonly platformId = inject(PLATFORM_ID);

  readonly accountValue = signal(125430.50);
  readonly dayChange = signal(2145.50);
  readonly dayChangePercent = signal(1.74);

  readonly showOrderModal = signal(false);

  readonly holdings = signal<Holding[]>([
    { symbol: 'AAPL', name: 'Apple', shares: 50, price: 240, value: 12000, percent: 9.5 },
    { symbol: 'MSFT', name: 'Microsoft', shares: 30, price: 420, value: 12600, percent: 10.0 },
    { symbol: 'GOOGL', name: 'Google', shares: 20, price: 195, value: 3900, percent: 3.1 },
    { symbol: 'AMZN', name: 'Amazon', shares: 15, price: 183, value: 2745, percent: 2.2 },
    { symbol: 'TSLA', name: 'Tesla', shares: 40, price: 298, value: 11920, percent: 9.5 },
    { symbol: 'BRK.B', name: 'Berkshire', shares: 10, price: 415, value: 4150, percent: 3.3 },
    { symbol: 'META', name: 'Meta', shares: 25, price: 510, value: 12750, percent: 10.1 },
    { symbol: 'NVDA', name: 'NVIDIA', shares: 20, price: 875, value: 17500, percent: 13.9 },
    { symbol: 'JPM', name: 'JPMorgan', shares: 35, price: 201, value: 7035, percent: 5.6 },
    { symbol: 'JNJ', name: 'Johnson & Johnson', shares: 25, price: 160, value: 4000, percent: 3.2 },
    { symbol: 'V', name: 'Visa', shares: 18, price: 285, value: 5130, percent: 4.1 },
    { symbol: 'WMT', name: 'Walmart', shares: 22, price: 98, value: 2156, percent: 1.7 },
  ]);

  readonly accountHistory = signal<AccountHistory[]>([
    { date: 'Dec 29', value: 98500 },
    { date: 'Jan 5', value: 101200 },
    { date: 'Jan 15', value: 101200 },
    { date: 'Jan 22', value: 105800 },
    { date: 'Jan 29', value: 108400 },
    { date: 'Feb 5', value: 106900 },
    { date: 'Feb 12', value: 110300 },
    { date: 'Feb 19', value: 112800 },
    { date: 'Feb 26', value: 118600 },
    { date: 'Mar 5', value: 123285 },
    { date: 'Mar 12', value: 125430 },
  ]);

  readonly orderForm = signal<OrderForm>({
    symbol: '',
    quantity: '',
    orderType: 'Market Order',
    action: 'buy',
  });

  constructor() {
    effect(() => {
      if (isPlatformBrowser(this.platformId)) {
        if (this.showOrderModal()) {
          document.body.style.overflow = 'hidden';
        } else {
          document.body.style.overflow = 'auto';
        }
      }
    });
  }

  ngAfterViewInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.initializeCharts();
    }
  }

  private initializeCharts(): void {
    if (this.lineCanvas) {
      const lineCtx = this.lineCanvas.nativeElement.getContext('2d');
      if (lineCtx) {
        const dates = this.accountHistory().map(h => h.date);
        const values = this.accountHistory().map(h => h.value);

        new Chart(lineCtx, {
          type: 'line',
          data: {
            labels: dates,
            datasets: [{
              label: 'Account Value',
              data: values,
              borderColor: '#76A923',
              backgroundColor: 'rgba(118, 169, 35, 0.1)',
              fill: true,
              tension: 0.4,
              pointRadius: 4,
              pointBackgroundColor: '#006044',
              pointBorderColor: '#76A923',
              pointBorderWidth: 2,
            }],
          },
          options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
              legend: {
                display: true,
                position: 'top',
              },
              title: {
                display: false,
              },
            },
            scales: {
              y: {
                beginAtZero: false,
                ticks: {
                  callback: function(value) {
                    return '$' + value.toLocaleString();
                  },
                },
              },
            },
          } as ChartConfiguration['options'],
        });
      }
    }

    if (this.pieCanvas) {
      const pieCtx = this.pieCanvas.nativeElement.getContext('2d');
      if (pieCtx) {
        const symbols = this.holdings().map(h => h.symbol);
        const values = this.holdings().map(h => h.value);

        new Chart(pieCtx, {
          type: 'doughnut',
          data: {
            labels: symbols,
            datasets: [{
              data: values,
              backgroundColor: [
                '#76A923',
                '#006044',
                '#AF8A49',
                '#4caf50',
                '#2196F3',
                '#FF9800',
                '#9C27B0',
                '#E91E63',
                '#00BCD4',
                '#FFEB3B',
                '#8BC34A',
                '#795548',
              ],
              borderColor: '#ffffff',
              borderWidth: 2,
            }],
          },
          options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
              legend: {
                display: true,
                position: 'right',
              },
              title: {
                display: false,
              },
            },
          } as ChartConfiguration['options'],
        });
      }
    }
  }

  formatCurrency(value: number | string): string {
    const num = typeof value === 'string' ? parseFloat(value) : value;
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2 }).format(num);
  }

  getChangeSign(value: number | string): string {
    const num = typeof value === 'string' ? parseFloat(value) : value;
    return num > 0 ? '+' : '';
  }

  openOrderModal(): void {
    this.showOrderModal.set(true);
  }

  closeOrderModal(): void {
    this.showOrderModal.set(false);
  }

  updateOrderForm(field: 'symbol' | 'quantity' | 'orderType' | 'action', value: string | 'buy' | 'sell'): void {
    this.orderForm.update(form => ({
      ...form,
      [field]: value,
    }));
  }

  submitOrder(): void {
    const form = this.orderForm();
    if (form.symbol && form.quantity) {
      alert(`Order placed: ${form.action.toUpperCase()} ${form.quantity} shares of ${form.symbol}`);
      this.closeOrderModal();
      this.orderForm.set({
        symbol: '',
        quantity: '',
        orderType: 'Market Order',
        action: 'buy',
      });
    }
  }

}
