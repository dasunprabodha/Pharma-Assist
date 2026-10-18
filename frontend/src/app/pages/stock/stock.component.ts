import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { RuleRow, StockRow } from '../../core/models';
import { IconComponent } from '../../shared/icon.component';
import { TooltipDirective } from '../../shared/tooltip.directive';

@Component({
  selector: 'app-stock',
  imports: [FormsModule, IconComponent, TooltipDirective],
  templateUrl: './stock.component.html',
  styleUrl: './stock.component.css'
})
export class StockComponent implements OnInit {
  private readonly api = inject(ApiService);

  readonly tab = signal<'stock' | 'rules'>('stock');
  readonly stock = signal<StockRow[]>([]);
  readonly rules = signal<RuleRow[]>([]);
  readonly query = signal('');
  readonly inStockOnly = signal(false);

  readonly filteredStock = computed(() => {
    const q = this.query().trim().toLowerCase();
    return this.stock().filter(
      (r) =>
        (!this.inStockOnly() || r.in_stock === 'yes') &&
        (!q || [r.brand_name, r.generic_name, r.therapeutic_class].some((v) => v.toLowerCase().includes(q)))
    );
  });

  readonly filteredRules = computed(() => {
    const q = this.query().trim().toLowerCase();
    return this.rules().filter(
      (r) => !q || [r.item_a, r.item_b, r.rule_type, r.warning_message].some((v) => v.toLowerCase().includes(q))
    );
  });

  readonly outOfStock = computed(() => this.stock().filter((r) => r.in_stock !== 'yes').length);

  ngOnInit(): void {
    this.api.stock().subscribe((s) => this.stock.set(s));
    this.api.rules().subscribe((r) => this.rules.set(r));
  }

  severityClass(severity: string): string {
    return severity === 'major' ? 'bad' : severity === 'moderate' ? 'warn' : '';
  }
}
