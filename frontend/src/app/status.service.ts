import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Response of GET /api/status. Contains no credential values. */
export interface ConfigStatus {
  credentialsFound: boolean;
  profile: string | null;
  region: string;
  regionSet: boolean;
  modelId: string;
  modelIdSet: boolean;
  stockItems: number;
  interactionRules: number;
}

@Injectable({ providedIn: 'root' })
export class StatusService {
  private readonly http = inject(HttpClient);

  getStatus(): Observable<ConfigStatus> {
    return this.http.get<ConfigStatus>('/api/status');
  }
}
