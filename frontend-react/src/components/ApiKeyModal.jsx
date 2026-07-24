import { useState } from 'react';
import styles from './ApiKeyModal.module.css';

/**
 * ApiKeyModal — shown on first launch if no TomTom API key is stored.
 * Key is persisted in localStorage so the user only has to enter it once.
 *
 * Get a free key at: https://developer.tomtom.com (no credit card required)
 */
export default function ApiKeyModal({ onSave }) {
  const [key, setKey] = useState('');
  const [error, setError] = useState('');

  function handleSave() {
    const trimmed = key.trim();
    if (!trimmed || trimmed.length < 20) {
      setError('Please enter a valid TomTom API key (at least 20 characters).');
      return;
    }
    localStorage.setItem('tomtom_api_key', trimmed);
    onSave(trimmed);
  }

  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <div className={styles.icon}>🗺️</div>
        <h2>Live Traffic Setup</h2>
        <p className={styles.desc}>
          This app uses the <strong>TomTom Routing API</strong> to show real-time traffic
          conditions and compute the fastest evacuation route based on what's happening
          on Mumbai roads right now.
        </p>

        <div className={styles.steps}>
          <div className={styles.step}>
            <span className={styles.stepNum}>1</span>
            <span>Go to <a href="https://developer.tomtom.com" target="_blank" rel="noreferrer">developer.tomtom.com</a> → Sign up free (no credit card)</span>
          </div>
          <div className={styles.step}>
            <span className={styles.stepNum}>2</span>
            <span>Dashboard → My Apps → Create API Key</span>
          </div>
          <div className={styles.step}>
            <span className={styles.stepNum}>3</span>
            <span>Paste your key below — saved locally, never shared</span>
          </div>
        </div>

        <input
          className={styles.input}
          type="text"
          placeholder="Paste your TomTom API key here..."
          value={key}
          onChange={e => { setKey(e.target.value); setError(''); }}
          onKeyDown={e => e.key === 'Enter' && handleSave()}
        />
        {error && <p className={styles.error}>{error}</p>}

        <button className={styles.btn} onClick={handleSave}>
          Activate Live Traffic →
        </button>

        <p className={styles.free}>✅ Free tier: 2,500 calls/day — enough for full testing</p>
      </div>
    </div>
  );
}
