import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Output } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { LocalAuthService, LocalUser } from '@services/local-auth.service';

@Component({
  selector: 'app-local-auth',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './local-auth.component.html',
  styleUrls: ['./local-auth.component.less'],
})
export class LocalAuthComponent {
  @Output() authenticated = new EventEmitter<LocalUser>();

  isRegister = false;
  submitting = false;
  errorMessage = '';

  readonly form = this.formBuilder.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(64),
      Validators.pattern(/^[A-Za-z0-9_.-]+$/)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirmPassword: [''],
    realName: [''],
    email: ['', Validators.email],
  });

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly authService: LocalAuthService,
  ) {}

  switchMode(register: boolean): void {
    this.isRegister = register;
    this.errorMessage = '';
    this.form.controls.realName.setValidators(register ? [Validators.required, Validators.maxLength(100)] : []);
    this.form.controls.confirmPassword.setValidators(register ? [Validators.required] : []);
    this.form.controls.realName.updateValueAndValidity();
    this.form.controls.confirmPassword.updateValueAndValidity();
  }

  submit(): void {
    this.errorMessage = '';
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.errorMessage = '请检查表单内容后重试';
      return;
    }
    const value = this.form.getRawValue();
    if (this.isRegister && value.password !== value.confirmPassword) {
      this.errorMessage = '两次输入的密码不一致';
      return;
    }

    this.submitting = true;
    const request$ = this.isRegister
      ? this.authService.register({
          username: value.username,
          password: value.password,
          realName: value.realName,
          email: value.email || undefined,
        })
      : this.authService.login({ username: value.username, password: value.password });

    request$.pipe(finalize(() => this.submitting = false)).subscribe({
      next: user => this.authenticated.emit(user),
      error: (error: HttpErrorResponse) => {
        this.errorMessage = error.error?.message || '操作失败，请稍后重试';
      },
    });
  }

  fieldInvalid(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.dirty || control.touched);
  }
}
