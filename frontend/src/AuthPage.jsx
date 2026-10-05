import React, { useState } from 'react';
import { api, setTokens } from './api.js';
import './auth-admin.css';

function validateForm({ mode, email, password, confirmPassword, fullName, phone }) {
  const errors = {};
  const trimmedEmail = email.trim();
  const trimmedName = fullName.trim();

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
    errors.email = 'Enter a valid email address.';
  }

  if (mode === 'signup' && trimmedName.length < 2) {
    errors.fullName = 'Full name must contain at least 2 characters.';
  }

  if (!/(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{8,72}$/.test(password)) {
    errors.password = 'Use 8–72 characters with upper, lower, number and special character.';
  }

  if (mode === 'signup' && password !== confirmPassword) {
    errors.confirmPassword = 'Passwords do not match.';
  }

  if (mode === 'signup' && phone && !/^\+?[0-9]{10,15}$/.test(phone.trim())) {
    errors.phone = 'Use 10–15 digits, optionally starting with +.';
  }

  return errors;
}

export default function AuthPage({ mode = 'login', onAuthenticated, onNavigate, initialEmail = '' }) {
  const [activeMode, setActiveMode] = useState(mode);
  const [email, setEmail] = useState(initialEmail);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState(null);
  const [busy, setBusy] = useState(false);

  const switchMode = (next) => {
    setActiveMode(next);
    setErrors({});
    setMessage(null);
    setPassword('');
    setConfirmPassword('');
  };

  const submit = async (event) => {
    event.preventDefault();
    setMessage(null);

    const nextErrors = validateForm({
      mode: activeMode,
      email,
      password,
      confirmPassword,
      fullName,
      phone,
    });

    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;

    setBusy(true);
    try {
      if (activeMode === 'signup') {
        await api.register({
          email: email.trim(),
          password,
          fullName: fullName.trim(),
          phone: phone.trim() || null,
        });
        switchMode('login');
        setMessage('Account created successfully. Sign in to continue.');
        return;
      }

      const tokens = await api.login({ email: email.trim(), password });
      setTokens(tokens);
      const user = await api.me();
      onAuthenticated(user);
    } catch (error) {
      setErrors({});
      setMessage(error.message || 'Unable to complete the request.');
    } finally {
      setBusy(false);
    }
  };

  const demoLogin = () => {
    setActiveMode('login');
    setEmail('user@tickethub.dev');
    setPassword('User1234!');
    setErrors({});
    setMessage('Demo credentials filled. Press Sign in.');
  };

  return (
    <div className="auth-page">
      <div className="auth-shell">
        <button type="button" className="auth-brand" onClick={() => onNavigate('home')}>
          <span className="brand-mark">T</span>
          <span>TicketHub</span>
        </button>

        <div className="auth-card">
          <div className="auth-tabs">
            <button
              type="button"
              className={activeMode === 'login' ? 'auth-tab active' : 'auth-tab'}
              onClick={() => switchMode('login')}
            >
              Sign in
            </button>
            <button
              type="button"
              className={activeMode === 'signup' ? 'auth-tab active' : 'auth-tab'}
              onClick={() => switchMode('signup')}
            >
              Create account
            </button>
          </div>

          <div className="auth-heading">
            <span className="eyebrow">TICKETHUB ACCOUNT</span>
            <h1>{activeMode === 'login' ? 'Welcome back' : 'Create your account'}</h1>
            <p>
              {activeMode === 'login'
                ? 'Sign in to manage bookings and reserve seats.'
                : 'Create a secure account to book and manage your tickets.'}
            </p>
          </div>

          {message && <div className="auth-message">{message}</div>}

          <form className="auth-form" onSubmit={submit} noValidate>
            {activeMode === 'signup' && (
              <>
                <label>
                  Full name
                  <input
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    autoComplete="name"
                    placeholder="Your full name"
                  />
                  {errors.fullName && <small className="field-error">{errors.fullName}</small>}
                </label>

                <label>
                  Phone <span className="optional">Optional</span>
                  <input
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    autoComplete="tel"
                    placeholder="+919876543210"
                  />
                  {errors.phone && <small className="field-error">{errors.phone}</small>}
                </label>
              </>
            )}

            <label>
              Email address
              <input
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
              />
              {errors.email && <small className="field-error">{errors.email}</small>}
            </label>

            <label>
              Password
              <input
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                type="password"
                autoComplete={activeMode === 'login' ? 'current-password' : 'new-password'}
                placeholder="Enter your password"
              />
              {errors.password && <small className="field-error">{errors.password}</small>}
            </label>

            {activeMode === 'signup' && (
              <label>
                Confirm password
                <input
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  type="password"
                  autoComplete="new-password"
                  placeholder="Re-enter your password"
                />
                {errors.confirmPassword && <small className="field-error">{errors.confirmPassword}</small>}
              </label>
            )}

            <button className="primary-button full-width auth-submit" type="submit" disabled={busy}>
              {busy ? 'Please wait…' : activeMode === 'login' ? 'Sign in' : 'Create account'}
            </button>
          </form>

          {activeMode === 'login' && (
            <button type="button" className="demo-link" onClick={demoLogin}>
              Use demo user credentials
            </button>
          )}

          <button type="button" className="auth-back" onClick={() => onNavigate('home')}>
            ← Back to TicketHub
          </button>
        </div>

        <p className="auth-footnote">
          Your session uses the platform's JWT access/refresh-token flow and is kept in this browser session.
        </p>
      </div>
    </div>
  );
}
