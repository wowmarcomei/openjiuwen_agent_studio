import { HttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { LocalAuthService, LocalUser } from './local-auth.service';

describe('LocalAuthService', () => {
  let http: jasmine.SpyObj<HttpClient>;
  let service: LocalAuthService;

  const user: LocalUser = {
    userId: 'user-1',
    username: 'laomei',
    realName: '老梅',
    domainId: 'domain-1',
    projectId: 'project-1',
  };

  beforeEach(() => {
    http = jasmine.createSpyObj<HttpClient>('HttpClient', ['get', 'post']);
    service = new LocalAuthService(http);
  });

  it('publishes the current user after loading the session', () => {
    http.get.and.returnValue(of(user));

    service.me().subscribe();

    service.currentUser$.subscribe((currentUser) => {
      expect(currentUser).toEqual(user);
    });
  });

  it('clears the current user after logout succeeds', () => {
    http.post.and.returnValue(of(user));
    service.login({ username: 'laomei', password: 'password' }).subscribe();
    http.post.and.returnValue(of(undefined));

    service.logout().subscribe();

    service.currentUser$.subscribe((currentUser) => {
      expect(currentUser).toBeNull();
    });
  });

  it('keeps the current user when logout fails', () => {
    http.post.and.returnValue(of(user));
    service.login({ username: 'laomei', password: 'password' }).subscribe();
    http.post.and.returnValue(throwError(() => new Error('network error')));

    service.logout().subscribe({ error: () => undefined });

    service.currentUser$.subscribe((currentUser) => {
      expect(currentUser).toEqual(user);
    });
  });
});
