import { Component, HostListener, input, signal } from '@angular/core';
import { IconComponent } from './icon.component';

/**
 * Small product image of a stock item; click to enlarge. The image is the pharmacy's own shelf photo if one was
 * added to data/drug_images/, otherwise a labelled illustration – it shows the pack to pick, not what the AI read.
 */
@Component({
  selector: 'app-drug-thumb',
  imports: [IconComponent],
  template: `
    <button type="button" class="thumb" [class.sm]="size() === 'sm'" [class.lg]="size() === 'lg'" (click)="open($event)"
            [attr.aria-label]="'Enlarge image of ' + label()" [title]="'Image of ' + label() + ' – click to enlarge'">
      <img [src]="src()" [alt]="'Pack image: ' + label()" loading="lazy" />
    </button>
    @if (enlarged()) {
      <div class="overlay" role="dialog" [attr.aria-label]="'Pack image: ' + label()" (click)="enlarged.set(false)">
        <figure (click)="$event.stopPropagation()">
          <img [src]="src()" [alt]="'Pack image: ' + label()" />
          <figcaption>
            <strong>{{ label() }}</strong>
            <span>Stock item to dispense. Check the pack, name and strength against the prescription before handing it over.</span>
          </figcaption>
          <button type="button" class="close" (click)="enlarged.set(false)" aria-label="Close"><app-icon name="x" /></button>
        </figure>
      </div>
    }
  `,
  styles: `
    :host { display: inline-flex; }
    .thumb { padding: 0; border: 1px solid var(--border); border-radius: 6px; background: #fff; cursor: zoom-in;
             width: 78px; height: 45px; overflow: hidden; flex-shrink: 0; }
    .thumb.sm { width: 46px; height: 27px; border-radius: 4px; }
    .thumb.lg { width: 130px; height: 75px; }
    .thumb img { width: 100%; height: 100%; object-fit: contain; display: block; }
    .thumb:hover { border-color: var(--accent); }
    .overlay { position: fixed; inset: 0; z-index: 200; background: rgb(0 0 0 / 0.65); display: grid; place-items: center; padding: 24px; cursor: zoom-out; }
    figure { position: relative; margin: 0; background: var(--surface-1); border-radius: 12px; padding: 18px; max-width: min(560px, 100%); cursor: default; }
    figure img { width: 100%; max-height: 60vh; object-fit: contain; display: block; background: #fff; border-radius: 8px; }
    figcaption { display: grid; gap: 4px; margin-top: 12px; font-size: 13px; color: var(--text-secondary); }
    figcaption strong { color: var(--text-primary); font-size: 15px; }
    .close { position: absolute; top: 8px; right: 8px; border: 0; background: var(--surface-2); color: var(--text-primary);
             width: 30px; height: 30px; border-radius: 50%; display: grid; place-items: center; cursor: pointer; }
  `
})
export class DrugThumbComponent {
  readonly src = input.required<string>();
  readonly label = input.required<string>();
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  readonly enlarged = signal(false);

  open(event: Event): void {
    event.stopPropagation();
    this.enlarged.set(true);
  }

  @HostListener('document:keydown.escape')
  close(): void {
    this.enlarged.set(false);
  }
}
