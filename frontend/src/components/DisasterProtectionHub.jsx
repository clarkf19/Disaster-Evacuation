import React, { useState, useEffect } from 'react';
import * as API from '../services/backendApi';
import styles from './DisasterProtectionHub.module.css';

const DEFAULT_GUIDES = {
  FLOOD: {
    disasterType: 'FLOOD',
    title: 'Monsoon Waterlogging & Flash Flood Protection',
    icon: '🌊',
    summary: 'Mumbai floods advance rapidly during high tides and heavy downpours. Protect against invisible open manholes, submerged electrical currents, and waterborne pathogens.',
    immediateActions: [
      'Immediately move to the 2nd floor or higher elevation in a structurally solid building.',
      'Turn off your electrical main switch at the fuse board to prevent electrocution.',
      'Do NOT wade through standing water — open BMC manholes are invisible underwater.',
      'Keep your emergency supply bag close at hand with medicines and water.'
    ],
    firstAidSteps: [
      {
        stepTitle: 'Drowning & Water Inhalation CPR',
        instruction: 'Place victim on back, tilt head back slightly, and perform 30 rapid chest compressions followed by 2 rescue breaths. Continue until medical help arrives.',
        warning: 'Do NOT press stomach to force water out; focus immediately on CPR chest compressions.'
      },
      {
        stepTitle: 'Submerged Wire Electrocution',
        instruction: 'Do NOT touch victim directly with bare hands in water. Turn off main circuit breaker or use a dry wooden/plastic pole to separate victim from wire.',
        warning: 'Always assume standing floodwater near light poles or transformers is energized.'
      },
      {
        stepTitle: 'Hypothermia & Waterborne Infection',
        instruction: 'Remove wet clothes immediately. Wrap victim in dry blankets or dry towels. Clean cuts with antiseptic to prevent Leptospirosis.',
        warning: 'Avoid direct contact with Mithi River or open gutter flood overflow.'
      }
    ],
    dos: [
      'Move up to 2nd floor or higher elevation immediately',
      'Keep 1916 (BMC Control Room) and 108 saved in speed dial',
      'Store drinking water, snacks, and medicines in waterproof plastic bags',
      'Unplug heavy appliances before water enters premise'
    ],
    donts: [
      'Do NOT attempt to drive through flooded underpasses (Sion/Andheri subway)',
      'Do NOT wade near electrical poles, transformers, or street lights',
      'Do NOT leave children unattended near waterlogged streets',
      'Do NOT drink unboiled tap water after flood inundation'
    ],
    essentialKit: [
      '2 Litres clean drinking water per person',
      'Waterproof zipper bag for ID & documents',
      'High-intensity LED torch + spare batteries',
      'First Aid kit (Band-Aids, Dettol, ORS, Paracetamol)',
      'Fully charged mobile phone + 20,000 mAh power bank'
    ]
  },
  FIRE: {
    disasterType: 'FIRE',
    title: 'Building Fire & Toxic Smoke Inhalation Safety',
    icon: '🔥',
    summary: 'Structural fires in high-rises spread smoke faster than flames. Toxic smoke causes unconsciousness in less than 90 seconds.',
    immediateActions: [
      'Crawl low under smoke — breathable oxygen stays within 30cm of the floor.',
      'Test doors with the BACK of your hand before opening. If hot, do NOT open.',
      'Do NOT use elevators under any circumstances. Take fire stairwells or balcony refuge.',
      'If trapped in a room, seal door gaps with wet towels or bedsheets to block toxic smoke.'
    ],
    firstAidSteps: [
      {
        stepTitle: 'Smoke Inhalation Rescue',
        instruction: 'Move victim to fresh outdoor air instantly. Loosen tight clothing around neck. If breathing stops, start CPR compressions.',
        warning: 'Do NOT force liquids into mouth if victim is semi-conscious or unconscious.'
      },
      {
        stepTitle: 'Thermal Burn Immediate Care',
        instruction: 'Cool burn under cool running tap water for 15 to 20 minutes. Cover loosely with sterile non-stick bandage or clean dry cloth.',
        warning: 'Do NOT apply ice, butter, toothpaste, grease, or oil on burnt skin.'
      },
      {
        stepTitle: 'Stop, Drop & Roll (Clothes on Fire)',
        instruction: 'If your clothes catch fire: Stop moving, Drop to ground, Roll over repeatedly while covering face with hands.',
        warning: 'Do NOT run — running fans the flames and accelerates burning.'
      }
    ],
    dos: [
      'Crawl low on hands and knees under toxic smoke',
      'Feel door handles with back of hand before opening',
      'Cover nose and mouth with a thick wet cloth or mask',
      'Signal from window using bright cloth or phone flashlight for fire brigade'
    ],
    donts: [
      'Do NOT take elevators or lifts during a fire alarm',
      'Do NOT open hot doors or doors where smoke is billowing underneath',
      'Do NOT re-enter a burning building to retrieve personal belongings',
      'Do NOT break windows unless needed for emergency ventilation'
    ],
    essentialKit: [
      'Thick cotton towels (wet for smoke filter)',
      'Heavy-duty leather or heat-resistant gloves',
      'Emergency whistle or siren for signaling',
      'Burn ointment (Burnol / Silver Sulfadiazine) & sterile gauze',
      'N95 / Smoke respirator masks'
    ]
  },
  BRIDGE_COLLAPSE: {
    disasterType: 'BRIDGE_COLLAPSE',
    title: 'Bridge & Structural Collapse Emergency Guide',
    icon: '🌉',
    summary: 'Structural collapses create heavy falling debris, crushing hazards, and dust suffocation. Fast stabilization saves lives.',
    immediateActions: [
      'If inside a collapsing structure: Drop, Cover, and Hold On under a heavy desk or load-bearing pillar.',
      'Protect head and neck with arms, pillows, or heavy clothing.',
      'If trapped under debris: cover nose with cloth, tap rhythmically on metal pipes so search teams locate you.',
      'Stay clear of dangling concrete slabs, snapped cables, and unstable edges.'
    ],
    firstAidSteps: [
      {
        stepTitle: 'Crush Injury & Heavy Bleeding',
        instruction: 'Apply direct firm pressure to bleeding wounds using sterile cloth or pressure bandage. Elevate injured limb if no fracture suspected.',
        warning: 'Do NOT pull out impaled objects; stabilize around the object.'
      },
      {
        stepTitle: 'Fracture & Spinal Immobilization',
        instruction: 'Keep injured limb completely still. If spinal injury is suspected, do NOT move patient unless immediate fire threatens life.',
        warning: 'Avoid twisting victim\'s neck or back under any condition.'
      },
      {
        stepTitle: 'Dust Inhalation & Airway Clearance',
        instruction: 'Clear dust/debris from mouth and nose. Place victim in lateral recovery position if breathing.',
        warning: 'Do NOT give food or water if surgery may be required soon.'
      }
    ],
    dos: [
      'Drop, Cover, and Hold On under heavy structural frame',
      'Tap on pipes or solid walls in sets of three (SOS rhythm) for rescue sonar',
      'Cover nose with cloth to prevent suffocation from fine silica dust',
      'Keep clear of overhead powerlines near collapse site'
    ],
    donts: [
      'Do NOT use lighters or matches near collapse zone (gas leaks cause explosions)',
      'Do NOT crowd near unstable debris edges where secondary collapse can occur',
      'Do NOT pull heavily trapped victims recklessly without trained NDRF support',
      'Do NOT shout continuously — conserve oxygen while trapped'
    ],
    essentialKit: [
      'Heavy-duty work gloves and protective dust goggles',
      'Emergency whistle for signalling search teams',
      'Tourniquets, elastic compression bandages, and splints',
      'High-energy protein bars and water sachets',
      'Multi-tool / Swiss knife and duct tape'
    ]
  },
  CHEMICAL_LEAK: {
    disasterType: 'CHEMICAL_LEAK',
    title: 'Chemical Leak & Toxic Industrial Hazard Protocol',
    icon: '☣️',
    summary: 'Industrial toxic gas releases (Ammonia, Chlorine, LPG) travel downwind rapidly. Immediate crosswind evacuation is vital.',
    immediateActions: [
      'Determine wind direction and immediately move PERPENDICULAR (crosswind) to the gas plume.',
      'Cover mouth and nose with a damp towel, wet cloth, or carbon mask.',
      'If sheltering indoors: shut all doors/windows, turn off ACs and exhaust fans, seal gaps with wet towels.',
      'If exposed to chemical liquid/gas: strip contaminated clothes immediately and wash skin under clean water for 15+ minutes.'
    ],
    firstAidSteps: [
      {
        stepTitle: 'Chemical Eye & Skin Decontamination',
        instruction: 'Flush exposed eyes and skin with continuous clean water for at least 15 to 20 minutes. Remove contaminated clothing under shower.',
        warning: 'Do NOT rub eyes or apply neutralizing chemicals without toxicological advice.'
      },
      {
        stepTitle: 'Toxic Inhalation First Aid',
        instruction: 'Move patient immediately to fresh air outside the plume. Keep victim in a semi-upright seated position to ease breathing.',
        warning: 'Administer 100% medical oxygen if trained personnel available.'
      },
      {
        stepTitle: 'LPG / Flammable Gas Protocol',
        instruction: 'If gas smell is present: turn off main gas cylinder valve. Do NOT flip electric switches or create sparks.',
        warning: 'Do NOT start motor vehicles or light matches in gas zone.'
      }
    ],
    dos: [
      'Move crosswind (perpendicular to wind direction) away from gas cloud',
      'Cover face with wet cotton towel to absorb water-soluble gases',
      'Seal all doors, windows, and AC vents with duct tape or damp sheets if trapped indoors',
      'Strip contaminated clothes immediately and wash skin with plenty of water'
    ],
    donts: [
      'Do NOT run downwind (in the exact direction the gas cloud is blowing)',
      'Do NOT flip light switches, use lighters, or operate phones near gas leaks',
      'Do NOT touch spilled liquid chemicals or walk through chemical puddles',
      'Do NOT re-enter contaminated area until cleared by authorities'
    ],
    essentialKit: [
      'Full-face gas mask or N95 carbon respirator masks',
      'Safety goggles (non-vented) to protect eyes from chemical fumes',
      'Duct tape and heavy plastic sheeting for room sealing',
      'Saline eye wash bottles and antiseptic skin cleansers',
      'Bottled drinking water (minimum 3 Litres)'
    ]
  }
};

const DEFAULT_HOSPITALS = [
  {
    id: 101,
    name: 'KEM Hospital (King Edward Memorial)',
    region: 'Central Mumbai',
    area: 'Parel',
    address: 'Acharya Donde Marg, Parel, Mumbai, Maharashtra 400012',
    emergencyPhone: '022-24107000',
    alternatePhone: '022-24107687',
    specialties: ['24x7 Level-1 Trauma Center', 'Poison Control & Toxicology', 'Hyperbaric Drowning Unit', 'Blood Bank'],
    relevantDisasters: ['FLOOD', 'FIRE', 'CHEMICAL_LEAK', 'BRIDGE_COLLAPSE'],
    totalBeds: 1800,
    icuAvailable: true
  },
  {
    id: 102,
    name: 'Lilavati Hospital & Research Centre',
    region: 'Bandra & Western Suburbs',
    area: 'Bandra West',
    address: 'A-791, Bandra Reclamation, Bandra West, Mumbai 400050',
    emergencyPhone: '022-26751000',
    alternatePhone: '022-26568000',
    specialties: ['Advanced Burn ICU', 'Cardiac & Neuro Trauma', 'Emergency Surgery', '24x7 Ambulance'],
    relevantDisasters: ['FIRE', 'BRIDGE_COLLAPSE', 'FLOOD'],
    totalBeds: 320,
    icuAvailable: true
  },
  {
    id: 103,
    name: 'LTMG Hospital (Sion Hospital)',
    region: 'Central Mumbai',
    area: 'Sion',
    address: 'RB2 Rd, Sion West, Mumbai, Maharashtra 400022',
    emergencyPhone: '022-24076381',
    alternatePhone: '022-24063000',
    specialties: ['Disaster & Mass Casualty Ward', 'Trauma & Fracture Unit', 'Burn & Plastic Surgery', 'Paediatric Emergency'],
    relevantDisasters: ['FLOOD', 'BRIDGE_COLLAPSE', 'FIRE'],
    totalBeds: 1400,
    icuAvailable: true
  },
  {
    id: 104,
    name: 'Breach Candy Hospital',
    region: 'South Mumbai',
    area: 'Breach Candy',
    address: '60A, Bhulabhai Desai Marg, Breach Candy, Cumballa Hill, Mumbai 400026',
    emergencyPhone: '022-23667788',
    alternatePhone: '022-23667000',
    specialties: ['24x7 Casualty & Emergency', 'Cardiac Emergency', 'Intensive Care Unit'],
    relevantDisasters: ['FIRE', 'BRIDGE_COLLAPSE'],
    totalBeds: 212,
    icuAvailable: true
  },
  {
    id: 105,
    name: 'BYL Nair Charitable Hospital',
    region: 'South Mumbai',
    area: 'Mumbai Central',
    address: 'Dr AL Nair Rd, Near Mumbai Central Station, Mumbai 400008',
    emergencyPhone: '022-23027000',
    alternatePhone: '022-23081418',
    specialties: ['Toxicology & Chemical Poisoning', 'Major Trauma Center', 'Emergency Resuscitation'],
    relevantDisasters: ['CHEMICAL_LEAK', 'BRIDGE_COLLAPSE', 'FLOOD'],
    totalBeds: 1300,
    icuAvailable: true
  },
  {
    id: 106,
    name: 'Cooper Hospital (HBT Medical College)',
    region: 'Bandra & Western Suburbs',
    area: 'Vile Parle West',
    address: 'U 15, Juhu Scheme, Vile Parle West, Mumbai 400056',
    emergencyPhone: '022-26207254',
    alternatePhone: '022-26207256',
    specialties: ['Level-2 Trauma Center', 'Flood Emergency Response', 'General Surgery'],
    relevantDisasters: ['FLOOD', 'BRIDGE_COLLAPSE'],
    totalBeds: 600,
    icuAvailable: true
  },
  {
    id: 107,
    name: 'Kokilaben Dhirubhai Ambani Hospital',
    region: 'Bandra & Western Suburbs',
    area: 'Andheri West',
    address: 'Rao Saheb Achutrao Patwardhan Marg, Four Bungalows, Andheri West, Mumbai 400053',
    emergencyPhone: '022-42696969',
    alternatePhone: '022-30999999',
    specialties: ['24x7 Critical Care & Trauma', 'Advanced Burn Unit', 'Stroke & Neuro Emergency', 'Air Ambulance'],
    relevantDisasters: ['FIRE', 'CHEMICAL_LEAK', 'BRIDGE_COLLAPSE'],
    totalBeds: 750,
    icuAvailable: true
  },
  {
    id: 108,
    name: 'Fortis Hospital Mulund',
    region: 'Eastern Suburbs & Thane',
    area: 'Mulund West',
    address: 'Mulund - Goregaon Link Rd, Bhandup West, Mumbai 400078',
    emergencyPhone: '022-67994444',
    alternatePhone: '022-67994100',
    specialties: ['24x7 Trauma & Emergency', 'Cardiac Resuscitation', 'ICU & Ventilator Care'],
    relevantDisasters: ['BRIDGE_COLLAPSE', 'FLOOD', 'FIRE'],
    totalBeds: 315,
    icuAvailable: true
  },
  {
    id: 109,
    name: 'Bombay Hospital & Medical Research Centre',
    region: 'South Mumbai',
    area: 'Marine Lines',
    address: '12, Vitthaldas Thackersey Marg, New Marine Lines, Marine Lines, Mumbai 400020',
    emergencyPhone: '022-22067676',
    alternatePhone: '022-22082121',
    specialties: ['Emergency Casualty Ward', 'Polytrauma Unit', 'Neurosurgery ICU'],
    relevantDisasters: ['BRIDGE_COLLAPSE', 'FIRE'],
    totalBeds: 830,
    icuAvailable: true
  },
  {
    id: 110,
    name: 'Kasturba Hospital for Infectious Diseases',
    region: 'Central Mumbai',
    area: 'Chinchpokli',
    address: 'Sane Guruji Marg, Arthur Road, Chinchpokli, Mumbai 400011',
    emergencyPhone: '022-23083901',
    alternatePhone: '022-23092424',
    specialties: ['Specialised Isolation & Biological Hazard', 'Chemical Contamination Care', 'Epidemic Emergency'],
    relevantDisasters: ['CHEMICAL_LEAK', 'FLOOD'],
    totalBeds: 500,
    icuAvailable: true
  },
  {
    id: 111,
    name: 'KB Bhabha Hospital Kurla',
    region: 'Central Mumbai',
    area: 'Kurla West',
    address: 'Belgrami Rd, Near Railway Station, Kurla West, Mumbai 400070',
    emergencyPhone: '022-26500241',
    alternatePhone: '022-26500244',
    specialties: ['Flood Inundation Emergency Ward', 'Casualty & First Aid', 'Mithi River Disaster Response'],
    relevantDisasters: ['FLOOD', 'BRIDGE_COLLAPSE'],
    totalBeds: 300,
    icuAvailable: true
  },
  {
    id: 112,
    name: 'Jupiter Hospital Thane',
    region: 'Eastern Suburbs & Thane',
    area: 'Thane West',
    address: 'Eastern Express Highway, Next to Viviana Mall, Thane West, Maharashtra 400601',
    emergencyPhone: '022-21725555',
    alternatePhone: '022-21725656',
    specialties: ['Level-1 Regional Trauma Center', 'Burn ICU', 'Multi-organ ICU', '24x7 Ambulance'],
    relevantDisasters: ['FIRE', 'BRIDGE_COLLAPSE', 'CHEMICAL_LEAK', 'FLOOD'],
    totalBeds: 350,
    icuAvailable: true
  }
];

export default function DisasterProtectionHub({ selectedDisasterType = 'FLOOD' }) {
  const [activeType, setActiveType] = useState(selectedDisasterType);
  const [selectedRegion, setSelectedRegion] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [copiedId, setCopiedId] = useState(null);
  const [checkedKitItems, setCheckedKitItems] = useState({});

  // Dynamic API state with robust defaults
  const [guides, setGuides] = useState(DEFAULT_GUIDES);
  const [hospitals, setHospitals] = useState(DEFAULT_HOSPITALS);
  const [activeTabSection, setActiveTabSection] = useState('firstaid'); // 'firstaid' | 'hospitals' | 'checklist'

  useEffect(() => {
    if (selectedDisasterType) {
      setActiveType(selectedDisasterType);
    }
  }, [selectedDisasterType]);

  useEffect(() => {
    fetchDisasterData();
  }, []);

  async function fetchDisasterData() {
    try {
      const apiGuides = await API.getDisasterProtectionGuides();
      if (apiGuides && Object.keys(apiGuides).length > 0) {
        setGuides(apiGuides);
      }
    } catch (e) {
      console.log('Using pre-loaded emergency guides.');
    }

    try {
      const apiHospitals = await API.getEmergencyHospitals();
      if (apiHospitals && apiHospitals.length > 0) {
        setHospitals(apiHospitals);
      }
    } catch (e) {
      console.log('Using pre-loaded Mumbai hospital directory.');
    }
  }

  const currentGuide = guides[activeType] || DEFAULT_GUIDES[activeType] || DEFAULT_GUIDES.FLOOD;

  // Filter hospitals
  const filteredHospitals = hospitals.filter(h => {
    const matchRegion = selectedRegion === 'ALL' || h.region === selectedRegion;
    const q = searchQuery.toLowerCase().trim();
    const matchQuery = !q || h.name.toLowerCase().includes(q) || h.area.toLowerCase().includes(q) || h.specialties.some(s => s.toLowerCase().includes(q));
    return matchRegion && matchQuery;
  });

  function handleCopyNumber(id, number) {
    navigator.clipboard.writeText(number);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  }

  function toggleKitItem(index) {
    setCheckedKitItems(prev => ({
      ...prev,
      [index]: !prev[index]
    }));
  }

  const disasterTypes = [
    { value: 'FLOOD', label: '🌊 Monsoon Flood' },
    { value: 'FIRE', label: '🔥 Building Fire' },
    { value: 'BRIDGE_COLLAPSE', label: '🌉 Bridge Damage' },
    { value: 'CHEMICAL_LEAK', label: '☣️ Chemical Hazard' },
  ];

  const regions = ['ALL', 'South Mumbai', 'Central Mumbai', 'Bandra & Western Suburbs', 'Eastern Suburbs & Thane'];

  return (
    <div className={styles.container}>
      {/* Header */}
      <div className={styles.header}>
        <div className={styles.headerTitleRow}>
          <span className={styles.shieldIcon}>🛡️</span>
          <div>
            <h2 className={styles.title}>Disaster Protection & Emergency Resource Hub</h2>
            <p className={styles.subtitle}>Instant First Aid, Mumbai Emergency Helplines & Verified Hospitals</p>
          </div>
        </div>

        {/* Disaster Selection Tabs */}
        <div className={styles.disasterTabs}>
          {disasterTypes.map(t => (
            <button
              key={t.value}
              className={`${styles.disasterTabBtn} ${activeType === t.value ? styles.activeDisasterTab : ''}`}
              onClick={() => setActiveType(t.value)}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {/* Quick Emergency Helplines Bar */}
      <div className={styles.helplineBar}>
        <div className={styles.helplineTitle}>
          <span className={styles.pulseDot} />
          <strong>Direct Emergency Helplines:</strong>
        </div>
        <div className={styles.helplineGrid}>
          <a href="tel:1916" className={styles.helplineBadge}>
            <span className={styles.badgeLabel}>BMC Disaster Control</span>
            <strong className={styles.badgeNum}>📞 1916</strong>
          </a>
          <a href="tel:108" className={styles.helplineBadge}>
            <span className={styles.badgeLabel}>Medical & Ambulance</span>
            <strong className={styles.badgeNum}>🚑 108</strong>
          </a>
          <a href="tel:101" className={styles.helplineBadge}>
            <span className={styles.badgeLabel}>Fire Brigade</span>
            <strong className={styles.badgeNum}>🚒 101</strong>
          </a>
          <a href="tel:112" className={styles.helplineBadge}>
            <span className={styles.badgeLabel}>Police Emergency</span>
            <strong className={styles.badgeNum}>👮 112</strong>
          </a>
          <a href="tel:02224107687" className={styles.helplineBadge}>
            <span className={styles.badgeLabel}>Poison Control (KEM)</span>
            <strong className={styles.badgeNum}>☣️ 022-24107687</strong>
          </a>
        </div>
      </div>

      {/* Sub-Navigation Tabs */}
      <div className={styles.sectionNav}>
        <button
          className={`${styles.navBtn} ${activeTabSection === 'firstaid' ? styles.activeNavBtn : ''}`}
          onClick={() => setActiveTabSection('firstaid')}
        >
          🏥 First Aid & Action Plan
        </button>
        <button
          className={`${styles.navBtn} ${activeTabSection === 'hospitals' ? styles.activeNavBtn : ''}`}
          onClick={() => setActiveTabSection('hospitals')}
        >
          🏥 Nearby Emergency Hospitals ({filteredHospitals.length})
        </button>
        <button
          className={`${styles.navBtn} ${activeTabSection === 'checklist' ? styles.activeNavBtn : ''}`}
          onClick={() => setActiveTabSection('checklist')}
        >
          🎒 Emergency Kit Checklist
        </button>
      </div>

      {/* SECTION 1: FIRST AID & ACTION PLAN */}
      {activeTabSection === 'firstaid' && (
        <div className={styles.tabBody}>
          {/* Summary Box */}
          <div className={styles.summaryBox}>
            <h3>{currentGuide.icon} {currentGuide.title}</h3>
            <p>{currentGuide.summary}</p>
          </div>

          {/* Immediate Actions (0-5 Mins) */}
          <div className={styles.cardSection}>
            <h4 className={styles.sectionHeading}>⚡ Immediate Actions (Next 0–5 Minutes)</h4>
            <div className={styles.immediateList}>
              {currentGuide.immediateActions?.map((act, idx) => (
                <div key={idx} className={styles.immediateItem}>
                  <span className={styles.stepNum}>{idx + 1}</span>
                  <p>{act}</p>
                </div>
              ))}
            </div>
          </div>

          {/* Step-by-Step First Aid Procedures */}
          <div className={styles.cardSection}>
            <h4 className={styles.sectionHeading}>🩹 Emergency First Aid Procedures</h4>
            <div className={styles.firstAidGrid}>
              {currentGuide.firstAidSteps?.map((step, idx) => (
                <div key={idx} className={styles.firstAidCard}>
                  <div className={styles.firstAidCardHeader}>
                    <h5>{step.stepTitle}</h5>
                  </div>
                  <p className={styles.instructionText}>{step.instruction}</p>
                  {step.warning && (
                    <div className={styles.warningBox}>
                      <strong>⚠️ CAUTION:</strong> {step.warning}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* Do's and Don'ts Grid */}
          <div className={styles.dosDontsGrid}>
            <div className={`${styles.dosBox}`}>
              <h4>✅ DO THIS (Life-Saving Steps)</h4>
              <ul>
                {currentGuide.dos?.map((item, idx) => (
                  <li key={idx}>✓ {item}</li>
                ))}
              </ul>
            </div>
            <div className={`${styles.dontsBox}`}>
              <h4>❌ DO NOT DO (Deadly Mistakes)</h4>
              <ul>
                {currentGuide.donts?.map((item, idx) => (
                  <li key={idx}>✕ {item}</li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      )}

      {/* SECTION 2: NEARBY EMERGENCY HOSPITALS */}
      {activeTabSection === 'hospitals' && (
        <div className={styles.tabBody}>
          {/* Filter & Search Bar */}
          <div className={styles.filterBar}>
            <div className={styles.searchWrapper}>
              <span className={styles.searchIcon}>🔍</span>
              <input
                type="text"
                className={styles.searchInput}
                placeholder="Search hospital name, area (e.g. Parel, Bandra), or specialty..."
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
              />
              {searchQuery && (
                <button className={styles.clearSearchBtn} onClick={() => setSearchQuery('')}>✕</button>
              )}
            </div>

            <div className={styles.regionFilterBtns}>
              {regions.map(r => (
                <button
                  key={r}
                  className={`${styles.regionBtn} ${selectedRegion === r ? styles.activeRegionBtn : ''}`}
                  onClick={() => setSelectedRegion(r)}
                >
                  {r}
                </button>
              ))}
            </div>
          </div>

          {/* Hospital Cards Grid */}
          <div className={styles.hospitalGrid}>
            {filteredHospitals.length === 0 ? (
              <div className={styles.emptyState}>
                <p>No emergency hospitals match your search criteria.</p>
              </div>
            ) : (
              filteredHospitals.map(h => {
                const isRecommended = h.relevantDisasters?.includes(activeType);
                return (
                  <div
                    key={h.id}
                    className={`${styles.hospitalCard} ${isRecommended ? styles.recommendedCard : ''}`}
                  >
                    {isRecommended && (
                      <div className={styles.recommendedBadge}>
                        ★ Recommended for {activeType.replace('_', ' ')}
                      </div>
                    )}
                    <div className={styles.hospitalHeader}>
                      <div>
                        <h4 className={styles.hospitalName}>{h.name}</h4>
                        <span className={styles.hospitalArea}>📍 {h.area} · {h.region}</span>
                      </div>
                    </div>

                    <p className={styles.hospitalAddress}>{h.address}</p>

                    {/* Specialties */}
                    <div className={styles.specialtyBadges}>
                      {h.specialties?.map((spec, sIdx) => (
                        <span key={sIdx} className={styles.specBadge}>{spec}</span>
                      ))}
                    </div>

                    {/* Direct Call & Copy Actions */}
                    <div className={styles.actionRow}>
                      <a
                        href={`tel:${h.emergencyPhone.replace(/[^0-9]/g, '')}`}
                        className={styles.callBtn}
                      >
                        📞 Call Casualty: {h.emergencyPhone}
                      </a>
                      <button
                        className={styles.copyBtn}
                        onClick={() => handleCopyNumber(h.id, h.emergencyPhone)}
                      >
                        {copiedId === h.id ? '✓ Copied!' : '📋 Copy'}
                      </button>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}

      {/* SECTION 3: EMERGENCY KIT CHECKLIST */}
      {activeTabSection === 'checklist' && (
        <div className={styles.tabBody}>
          <div className={styles.checklistCard}>
            <div className={styles.checklistHeader}>
              <h4>🎒 Essential Emergency Go-Bag Checklist</h4>
              <p>Check off items as you pack them before evacuating or sheltering.</p>
            </div>
            <div className={styles.checkGrid}>
              {currentGuide.essentialKit?.map((item, idx) => {
                const isChecked = !!checkedKitItems[idx];
                return (
                  <div
                    key={idx}
                    className={`${styles.checkItem} ${isChecked ? styles.checkItemDone : ''}`}
                    onClick={() => toggleKitItem(idx)}
                  >
                    <input
                      type="checkbox"
                      checked={isChecked}
                      onChange={() => {}}
                    />
                    <span className={styles.checkText}>{item}</span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
