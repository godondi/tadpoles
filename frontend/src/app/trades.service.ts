export interface Trade {
  id: string;
  symbol: string;
  company: string;
  action: 'buy' | 'sell';
  quantity: number;
  price: number;
  total: number;
  date: string;
  time: string;
  orderType: string;
  status: 'completed' | 'pending' | 'cancelled';
  commission: number;
}

import { Injectable } from '@angular/core';
import { signal, computed } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class TradesService {
  private trades = signal<Trade[]>([
    {
      id: 'TRD-2026-1001',
      symbol: 'AAPL',
      company: 'Apple Inc.',
      action: 'buy',
      quantity: 10,
      price: 190.25,
      total: 1902.50,
      date: 'September 30, 2026',
      time: '10:32 AM',
      orderType: 'Market Order',
      status: 'completed',
      commission: 5.00,
    },
    {
      id: 'TRD-2026-1000',
      symbol: 'SPY',
      company: 'S&P 500 ETF',
      action: 'sell',
      quantity: 5,
      price: 585.32,
      total: 2926.60,
      date: 'September 28, 2026',
      time: '2:15 PM',
      orderType: 'Limit Order',
      status: 'completed',
      commission: 5.00,
    },
    {
      id: 'TRD-2026-0999',
      symbol: 'MSFT',
      company: 'Microsoft Corp.',
      action: 'buy',
      quantity: 8,
      price: 415.80,
      total: 3326.40,
      date: 'September 27, 2026',
      time: '9:45 AM',
      orderType: 'Market Order',
      status: 'completed',
      commission: 5.00,
    },
    {
      id: 'TRD-2026-0998',
      symbol: 'TSLA',
      company: 'Tesla Inc.',
      action: 'buy',
      quantity: 3,
      price: 245.60,
      total: 736.80,
      date: 'September 25, 2026',
      time: '1:20 PM',
      orderType: 'Market Order',
      status: 'completed',
      commission: 5.00,
    },
    {
      id: 'TRD-2026-0997',
      symbol: 'NVDA',
      company: 'NVIDIA Corp.',
      action: 'sell',
      quantity: 6,
      price: 127.45,
      total: 764.70,
      date: 'September 23, 2026',
      time: '3:55 PM',
      orderType: 'Limit Order',
      status: 'completed',
      commission: 5.00,
    },
    {
      id: 'TRD-2026-0996',
      symbol: 'QQQ',
      company: 'Nasdaq ETF',
      action: 'buy',
      quantity: 15,
      price: 420.15,
      total: 6302.25,
      date: 'September 22, 2026',
      time: '11:10 AM',
      orderType: 'Market Order',
      status: 'completed',
      commission: 5.00,
    },
    {
      id: 'TRD-2026-0995',
      symbol: 'AMD',
      company: 'Advanced Micro Devices',
      action: 'buy',
      quantity: 12,
      price: 155.30,
      total: 1863.60,
      date: 'September 20, 2026',
      time: '2:30 PM',
      orderType: 'Market Order',
      status: 'completed',
      commission: 5.00,
    },
    {
      id: 'TRD-2026-0994',
      symbol: 'AMZN',
      company: 'Amazon.com Inc.',
      action: 'sell',
      quantity: 4,
      price: 185.42,
      total: 741.68,
      date: 'September 18, 2026',
      time: '10:05 AM',
      orderType: 'Market Order',
      status: 'completed',
      commission: 5.00,
    },
    {
      id: 'TRD-2026-0993',
      symbol: 'GOOGL',
      company: 'Alphabet Inc.',
      action: 'buy',
      quantity: 2,
      price: 165.78,
      total: 331.56,
      date: 'September 16, 2026',
      time: '11:45 AM',
      orderType: 'Limit Order',
      status: 'completed',
      commission: 5.00,
    },
    {
      id: 'TRD-2026-0992',
      symbol: 'META',
      company: 'Meta Platforms Inc.',
      action: 'buy',
      quantity: 7,
      price: 545.20,
      total: 3816.40,
      date: 'September 14, 2026',
      time: '1:15 PM',
      orderType: 'Market Order',
      status: 'completed',
      commission: 5.00,
    },
  ]);

  getTrades() {
    return this.trades.asReadonly();
  }

  getTradeById(id: string) {
    return computed(() => {
      return this.trades().find((trade) => trade.id === id);
    })();
  }

  getTotalCommissions = computed(() => {
    return this.trades().reduce((sum, trade) => sum + trade.commission, 0);
  });

  getTotalVolume = computed(() => {
    return this.trades().reduce((sum, trade) => sum + trade.total, 0);
  });

  getCompletedTrades = computed(() => {
    return this.trades().filter((trade) => trade.status === 'completed').length;
  });

  addTrade(trade: Trade): void {
    const current = this.trades();
    this.trades.set([trade, ...current]);
  }
}
