import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface LocalUser {
  userId: string;
  username: string;
  realName?: string;
  email?: string;
  domainId: string;
  projectId: string;
}

export interface LocalLoginPayload {
  username: string;
  password: string;
}

export interface LocalRegisterPayload extends LocalLoginPayload {
  realName: string;
  email?: string;
}

@Injectable({ providedIn: 'root' })
export class LocalAuthService {
  private readonly endpoint = `${window.location.origin}/auth/local`;

  constructor(private readonly http: HttpClient) {}

  me(): Observable<LocalUser> {
    return this.http.get<LocalUser>(`${this.endpoint}/me`, { withCredentials: true });
  }

  login(payload: LocalLoginPayload): Observable<LocalUser> {
    return this.http.post<LocalUser>(`${this.endpoint}/login`, payload, { withCredentials: true });
  }

  register(payload: LocalRegisterPayload): Observable<LocalUser> {
    return this.http.post<LocalUser>(`${this.endpoint}/register`, payload, { withCredentials: true });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.endpoint}/logout`, {}, { withCredentials: true });
  }
}
