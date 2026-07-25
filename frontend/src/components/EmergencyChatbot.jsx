import React, { useState, useEffect, useRef } from 'react';
import * as API from '../services/backendApi';
import styles from './EmergencyChatbot.module.css';

export default function EmergencyChatbot() {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState([
    {
      sender: 'bot',
      text: '### 🚨 Mumbai Emergency AI Assistant\n\nI am connected to real-time Mumbai disaster monitoring networks. Ask me about **flooding safety**, **fire evacuations**, **chemical spills**, **shelter availability**, or **emergency helplines**.',
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      actions: [
        { label: '🌊 Sion Flood Safety', query: 'What to do during Sion flooding?' },
        { label: '📞 Emergency Contacts', query: 'Show Mumbai emergency helpline numbers' },
        { label: '⛺ Locate Shelters', query: 'Find nearest open evacuation shelter' },
      ]
    },
  ]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isListening, setIsListening] = useState(false);
  const [activeTab, setActiveTab] = useState('all');

  const messagesEndRef = useRef(null);
  const recognitionRef = useRef(null);

  useEffect(() => {
    if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      const rec = new SpeechRecognition();
      rec.continuous = true;
      rec.interimResults = true;
      rec.lang = 'en-IN';
      rec.maxAlternatives = 1;

      rec.onstart = () => {
        setIsListening(true);
      };

      rec.onresult = (event) => {
        let currentTranscript = '';
        for (let i = event.resultIndex; i < event.results.length; ++i) {
          currentTranscript += event.results[i][0].transcript;
        }
        if (currentTranscript.trim()) {
          setInput(currentTranscript);
        }
      };

      rec.onerror = (event) => {
        console.warn('Speech recognition error:', event.error);
        if (event.error === 'not-allowed') {
          alert('Microphone permission denied. Please allow microphone access in your browser address bar.');
          setIsListening(false);
        } else if (event.error === 'no-speech') {
          // Ignore transient silence timeouts, keep listening state clean
        } else {
          setIsListening(false);
        }
      };

      rec.onend = () => {
        setIsListening(false);
      };

      recognitionRef.current = rec;
    }
  }, []);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    if (isOpen) {
      scrollToBottom();
    }
  }, [messages, isOpen, isLoading]);

  const toggleVoiceInput = () => {
    if (!recognitionRef.current) {
      alert('Voice speech input is not supported in your browser. Please use Chrome or Edge.');
      return;
    }

    if (isListening) {
      try {
        recognitionRef.current.stop();
      } catch (e) {}
      setIsListening(false);
    } else {
      try {
        setInput('');
        recognitionRef.current.start();
        setIsListening(true);
      } catch (e) {
        console.error('Speech recognition start error:', e);
        // If already started, try stopping first then restarting
        try {
          recognitionRef.current.stop();
          setTimeout(() => {
            recognitionRef.current.start();
            setIsListening(true);
          }, 200);
        } catch (err) {
          setIsListening(false);
        }
      }
    }
  };


  const handleSend = async (textToSend) => {
    const query = textToSend || input.trim();
    if (!query || isLoading) return;

    if (isListening && recognitionRef.current) {
      try { recognitionRef.current.stop(); } catch (e) {}
      setIsListening(false);
    }

    const userMsg = {
      sender: 'user',
      text: query,
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };

    setMessages((prev) => [...prev, userMsg]);
    if (!textToSend) setInput('');
    setIsLoading(true);


    try {
      const res = await API.sendChatMessage(query);
      const botMsg = {
        sender: 'bot',
        text: res.reply || 'No response received from safety assistant.',
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        suggestedPills: res.suggestedActions || []
      };
      setMessages((prev) => [...prev, botMsg]);
    } catch (err) {
      console.error('Chatbot error:', err);
      setMessages((prev) => [
        ...prev,
        {
          sender: 'bot',
          text: '### ⚠️ Emergency Helpline Fallback\n\nUnable to reach live AI server. For urgent emergencies in Mumbai, please call:\n- **BMC Disaster Cell**: 1916\n- **Ambulance**: 108\n- **Police**: 100',
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        },
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  const renderFormattedText = (text) => {
    if (!text) return null;
    const lines = text.split('\n');

    return lines.map((line, idx) => {
      let trimmed = line.trim();
      if (!trimmed) return <br key={idx} />;

      // Header 3
      if (trimmed.startsWith('### ')) {
        return <h3 key={idx} className={styles.msgHeader}>{parseBold(trimmed.substring(4))}</h3>;
      }

      // Bullet points
      if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
        return (
          <li key={idx} className={styles.msgListItem}>
            {parseBold(trimmed.substring(2))}
          </li>
        );
      }

      // Numbered items
      if (/^\d+\.\s/.test(trimmed)) {
        const content = trimmed.replace(/^\d+\.\s/, '');
        return (
          <div key={idx} className={styles.msgNumItem}>
            <span className={styles.numBadge}>{trimmed.match(/^\d+\./)[0]}</span>
            <span>{parseBold(content)}</span>
          </div>
        );
      }

      return <p key={idx} className={styles.msgParagraph}>{parseBold(line)}</p>;
    });
  };

  const parseBold = (str) => {
    const parts = str.split(/(\*\*.*?\*\*)/g);
    return parts.map((part, i) => {
      if (part.startsWith('**') && part.endsWith('**')) {
        return <strong key={i} className={styles.boldHighlight}>{part.slice(2, -2)}</strong>;
      }
      return part;
    });
  };

  const quickPills = [
    { label: '🌊 Flood Precautions', query: 'What to do during monsoon flooding in Sion?' },
    { label: '📞 Helplines (1916)', query: 'Show all Mumbai emergency helpline phone numbers' },
    { label: '⛺ Open Shelters', query: 'Which evacuation shelters are open right now?' },
    { label: '☣️ Gas Spill Protocol', query: 'Safety precautions for chemical leak in Chembur' },
    { label: '🚨 Fire Evacuation', query: 'Building fire emergency safety rules' },
  ];

  return (
    <div className={styles.floatingContainer}>
      {/* Trigger Button */}
      {!isOpen && (
        <button className={styles.triggerBtn} onClick={() => setIsOpen(true)}>
          <span className={styles.pulseDot} />
          <span className={styles.triggerIcon}>🤖</span>
          <span className={styles.triggerText}>Emergency AI</span>
        </button>
      )}

      {/* Chat Window Modal */}
      {isOpen && (
        <div className={styles.chatWindow}>
          {/* Header */}
          <div className={styles.header}>
            <div className={styles.headerInfo}>
              <div className={styles.botAvatar}>
                🤖
                <span className={styles.onlineBadge} />
              </div>
              <div>
                <h3 className={styles.headerTitle}>
                  Mumbai Disaster AI
                </h3>
                <p className={styles.headerSubtitle}>Real-time Safety & Evacuation Guidance</p>
              </div>
            </div>
            <button className={styles.closeBtn} onClick={() => setIsOpen(false)} title="Close Chat Assistant">
              ✖
            </button>
          </div>

          {/* Messages */}
          <div className={styles.messagesList}>
            {messages.map((m, idx) => (
              <div
                key={idx}
                className={`${styles.messageRow} ${
                  m.sender === 'user' ? styles.userRow : styles.botRow
                }`}
              >
                <div
                  className={`${styles.bubble} ${
                    m.sender === 'user' ? styles.userBubble : styles.botBubble
                  }`}
                >
                  {renderFormattedText(m.text)}
                  
                  {m.actions && (
                    <div className={styles.inlineActionGroup}>
                      {m.actions.map((act, i) => (
                        <button
                          key={i}
                          className={styles.inlineActionBtn}
                          onClick={() => handleSend(act.query)}
                        >
                          {act.label}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                <span className={styles.timestamp}>{m.time}</span>
              </div>
            ))}

            {isLoading && (
              <div className={`${styles.messageRow} ${styles.botRow}`}>
                <div className={`${styles.bubble} ${styles.botBubble}`}>
                  <div className={styles.typingIndicator}>
                    <span className={styles.dot} />
                    <span className={styles.dot} />
                    <span className={styles.dot} />
                    <span className={styles.typingText}>Consulting emergency database...</span>
                  </div>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Quick Action Pills */}
          <div className={styles.pillsContainer}>
            {quickPills.map((pill, i) => (
              <button
                key={i}
                className={styles.pillBtn}
                onClick={() => handleSend(pill.query)}
              >
                {pill.label}
              </button>
            ))}
          </div>

          {/* Input Form */}
          <form
            className={styles.inputForm}
            onSubmit={(e) => {
              e.preventDefault();
              handleSend();
            }}
          >
            <button
              type="button"
              className={`${styles.micBtn} ${isListening ? styles.micListening : ''}`}
              onClick={toggleVoiceInput}
              title={isListening ? 'Listening... Speak now' : 'Click to speak (Voice Input)'}
            >
              🎤
            </button>
            <input
              type="text"
              className={styles.inputField}
              placeholder={isListening ? 'Listening to your voice...' : 'Ask safety doubts or emergency questions...'}
              value={input}
              onChange={(e) => setInput(e.target.value)}
            />
            <button
              type="submit"
              className={styles.sendBtn}
              disabled={!input.trim() || isLoading}
            >
              ➔
            </button>
          </form>
        </div>
      )}
    </div>
  );
}
