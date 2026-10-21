import React, { useEffect, useState } from 'react';
import { ArrowRight, Eye, EyeOff, LoaderCircle, LockKeyhole, ShieldCheck } from 'lucide-react';
import { api } from './api.js';

export default function SessionBoundary({ children }) {
  const [session, setSession] = useState(null), [accessKey, setAccessKey] = useState(''), [show, setShow] = useState(false);
  const [error, setError] = useState(''), [busy, setBusy] = useState(false), [checking, setChecking] = useState(true);
  async function check(signal) {
    setChecking(true); setError('');
    try { const value = await api('/api/session', { signal }); if (!signal?.aborted) setSession(value); }
    catch (failure) { if (failure.name !== 'AbortError') setError(failure.message); }
    finally { if (!signal?.aborted) setChecking(false); }
  }
  useEffect(() => {
    const controller = new AbortController(); check(controller.signal);
    const expired = () => { setSession({ authenticated: false, mode: 'SCANPLUS administrator' }); setAccessKey(''); setError('Your session ended. Sign in again to continue.'); };
    window.addEventListener('scanplus-auth-required', expired);
    try { document.documentElement.classList.toggle('dark', localStorage.getItem('scanplus-theme') === 'dark'); } catch {}
    return () => { controller.abort(); window.removeEventListener('scanplus-auth-required', expired); };
  }, []);
  async function signIn(event) {
    event.preventDefault(); if (busy) return; setBusy(true); setError('');
    try { const value = await api('/api/session', { method: 'POST', body: { accessKey: accessKey.trim() } }); setAccessKey(''); setShow(false); setSession(value); }
    catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }
  async function signOut() {
    try { await api('/api/session', { method: 'DELETE' }); setSession({ authenticated: false, mode: 'SCANPLUS administrator' }); setAccessKey(''); setError(''); }
    catch (failure) { window.alert(failure.message); }
  }
  if (session?.authenticated) return children({ ...session, signOut });
  return <main className="signin-page"><div className="signin-card">
    <img className="signin-logo dark:hidden" src="/img/darklogo.svg" alt="ScanPlus POS Security"/><img className="signin-logo hidden dark:block" src="/img/lightlogo.svg" alt="ScanPlus POS Security"/>
    <div className="signin-icon"><LockKeyhole size={25}/></div>
    <h1>Administrator sign-in</h1><p className="signin-description">Your ScanPlus POS Security workspace.</p>
    {checking ? <div className="signin-loading" role="status"><LoaderCircle size={18} className="spin"/>Checking your session…</div> : session ? <form onSubmit={signIn}>
      <label htmlFor="admin-access-key">Administrator access key</label><div className="signin-input"><input id="admin-access-key" name="password" type={show ? 'text' : 'password'} autoComplete="current-password" autoCapitalize="off" spellCheck={false} required value={accessKey} onChange={event => setAccessKey(event.target.value)} placeholder="Enter your access key" disabled={busy} maxLength={128}/><button type="button" aria-label={show ? 'Hide access key' : 'Show access key'} onClick={() => setShow(!show)}>{show ? <EyeOff size={18}/> : <Eye size={18}/>}</button></div>
      {error && <p className="signin-error" role="alert">{error}</p>}
      <button className="signin-submit" type="submit" disabled={busy || !accessKey.trim()}>{busy ? <LoaderCircle size={16} className="spin"/> : <ArrowRight size={16}/>} {busy ? 'Signing in…' : 'Sign in'}</button>
    </form> : <><p className="signin-error" role="alert">{error || 'Cannot check your session.'}</p><button className="signin-submit" onClick={() => check()}>Try again</button></>}
    <p className="signin-note"><ShieldCheck size={14}/>Secure administrator access</p><div className="signin-footer"><img className="dark:hidden" src="/img/footerdark.png" alt="Powered by Velox"/><img className="hidden dark:block" src="/img/footerlight.png" alt="Powered by Velox"/></div>
  </div></main>;
}
