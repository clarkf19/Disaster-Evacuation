import { useEffect, useRef, useState } from 'react';
import * as API from '../services/backendApi';
import styles from './OperatorLogin.module.css';

/**
 * Dialog for unlocking operator mode. The token is checked with the server
 * before it is kept, so a typo is caught here instead of on the first action.
 */
export default function OperatorLogin({ onClose, onUnlocked }) {
  const [token, setToken] = useState('');
  const [state, setState] = useState('idle'); // idle | checking | wrong | error
  const inputRef = useRef(null);

  useEffect(() => {
    inputRef.current?.focus();
    const onKey = e => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  async function submit(e) {
    e.preventDefault();
    if (!token.trim()) return;
    setState('checking');
    try {
      if (await API.verifyOperatorToken(token)) {
        onUnlocked();
      } else {
        setState('wrong');
      }
    } catch {
      setState('error');
    }
  }

  return (
    <div className={styles.backdrop} onClick={onClose}>
      <form className={styles.dialog} onSubmit={submit} onClick={e => e.stopPropagation()}
            role="dialog" aria-modal="true" aria-labelledby="operator-title">
        <h2 id="operator-title">🔐 Operator login</h2>
        <p className={styles.text}>
          Emergency operators (for example the BMC control room) can place live hazard zones,
          flood the monsoon spots and run shelter drills. These change what <b>everyone</b> using the
          site sees, so they need the operator token.
        </p>
        <label className={styles.label} htmlFor="operator-token-input">Operator token</label>
        <input
          id="operator-token-input"
          ref={inputRef}
          className={styles.input}
          type="password"
          autoComplete="off"
          value={token}
          onChange={e => { setToken(e.target.value); setState('idle'); }}
          placeholder="Paste the operator token"
        />
        {state === 'wrong' && <p className={styles.error} role="alert">That token is not correct.</p>}
        {state === 'error' && <p className={styles.error} role="alert">Could not reach the server. Try again in a moment.</p>}
        <p className={styles.hint}>The token is the server's <code>ADMIN_TOKEN</code> setting. It is kept only for this browser tab.</p>
        <div className={styles.actions}>
          <button type="button" className={styles.cancel} onClick={onClose}>Cancel</button>
          <button type="submit" className={styles.submit} disabled={!token.trim() || state === 'checking'}>
            {state === 'checking' ? 'Checking…' : 'Unlock operator mode'}
          </button>
        </div>
      </form>
    </div>
  );
}
