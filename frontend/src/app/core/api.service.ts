import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import {
  ApiError,
  BedrockCheck,
  ConfigStatus,
  ConfirmedPrescription,
  Dashboard,
  Item,
  RuleRow,
  ScanResult,
  Source,
  StockRow
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  status(): Observable<ConfigStatus> {
    return this.http.get<ConfigStatus>('/api/status').pipe(catchError(toApiError));
  }

  checkBedrock(): Observable<BedrockCheck> {
    return this.http.post<BedrockCheck>('/api/status/check-bedrock', {}).pipe(catchError(toApiError));
  }

  scan(file: File): Observable<ScanResult> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<ScanResult>('/api/scans', form).pipe(catchError(toApiError));
  }

  /** Rendered page images of a PDF (no AI call). */
  preview(file: File): Observable<{ pages: string[] }> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<{ pages: string[] }>('/api/preview', form).pipe(catchError(toApiError));
  }

  nameCheck(items: Item[]): Observable<Item[]> {
    return this.http.post<Item[]>('/api/name-check', items).pipe(catchError(toApiError));
  }

  confirm(source: Source, scanId: string | null, items: Item[], corrections: number): Observable<ConfirmedPrescription> {
    return this.http
      .post<ConfirmedPrescription>('/api/prescriptions', { source, scan_id: scanId, items, corrections })
      .pipe(catchError(toApiError));
  }

  prescriptions(): Observable<ConfirmedPrescription[]> {
    return this.http.get<ConfirmedPrescription[]>('/api/prescriptions').pipe(catchError(toApiError));
  }

  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>('/api/dashboard').pipe(catchError(toApiError));
  }

  stock(): Observable<StockRow[]> {
    return this.http.get<StockRow[]>('/api/stock').pipe(catchError(toApiError));
  }

  rules(): Observable<RuleRow[]> {
    return this.http.get<RuleRow[]>('/api/rules').pipe(catchError(toApiError));
  }
}

/** Normalises HTTP failures to ApiError, so pages handle one shape. */
function toApiError(err: HttpErrorResponse): Observable<never> {
  if (err.error && typeof err.error === 'object' && 'error_type' in err.error) {
    return throwError(() => err.error as ApiError);
  }
  const apiError: ApiError =
    err.status === 0 || err.status === 502 || err.status === 504
      ? {
          error_type: 'network',
          message: 'Cannot reach the backend. Is it running on port 8080?',
          fallback_to_manual: true
        }
      : { error_type: 'ai_unavailable', message: `Unexpected server error (${err.status}).`, fallback_to_manual: true };
  return throwError(() => apiError);
}
