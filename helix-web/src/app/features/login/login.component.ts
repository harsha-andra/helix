import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService, UserRole } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatRadioModule,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly submitting = signal(false);
  readonly roles: { value: UserRole; label: string; description: string }[] = [
    { value: 'ADJUSTER', label: 'Adjuster', description: 'Full access — review claims, change status, file new claims.' },
    { value: 'MANAGER', label: 'Manager', description: 'Oversight access — file claims, view all queues and reports.' },
    { value: 'VIEWER', label: 'Viewer', description: 'Read-only access — cannot file claims or change status.' },
  ];

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    password: ['', [Validators.required]],
    role: ['ADJUSTER' as UserRole, [Validators.required]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const { name, role } = this.form.getRawValue();
    // Demo mode: any credentials succeed — this simulates the latency of a real OAuth2 exchange.
    setTimeout(() => {
      this.auth.login(name, role);
      const redirectTo = this.route.snapshot.queryParamMap.get('redirectTo') ?? '/dashboard';
      this.router.navigateByUrl(redirectTo);
    }, 450);
  }
}
