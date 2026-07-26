package com.mumbai.evacuation.service;

import com.mumbai.evacuation.dto.DisasterProtectionGuideDTO;
import com.mumbai.evacuation.dto.DisasterProtectionGuideDTO.FirstAidStep;
import com.mumbai.evacuation.dto.HospitalDTO;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DisasterInfoService {

    private final List<HospitalDTO> hospitals = new ArrayList<>();
    private final Map<String, DisasterProtectionGuideDTO> guides = new HashMap<>();

    public DisasterInfoService() {
        initHospitals();
        initGuides();
    }

    private void initHospitals() {
        hospitals.add(new HospitalDTO(
            101, "KEM Hospital (King Edward Memorial)", "Central Mumbai", "Parel",
            "Acharya Donde Marg, Parel, Mumbai, Maharashtra 400012",
            "022-24107000", "022-24107687", 18.9986, 72.8423,
            Arrays.asList("24x7 Level-1 Trauma Center", "Poison Control & Toxicology", "Hyperbaric Drowning Unit", "Blood Bank"),
            Arrays.asList("FLOOD", "FIRE", "CHEMICAL_LEAK", "BRIDGE_COLLAPSE"), 1800, true
        ));

        hospitals.add(new HospitalDTO(
            102, "Lilavati Hospital & Research Centre", "Bandra & Western Suburbs", "Bandra West",
            "A-791, Bandra Reclamation, Bandra West, Mumbai 400050",
            "022-26751000", "022-26568000", 19.0512, 72.8286,
            Arrays.asList("Advanced Burn ICU", "Cardiac & Neuro Trauma", "Emergency Surgery", "24x7 Ambulance"),
            Arrays.asList("FIRE", "BRIDGE_COLLAPSE", "FLOOD"), 320, true
        ));

        hospitals.add(new HospitalDTO(
            103, "LTMG Hospital (Sion Hospital)", "Central Mumbai", "Sion",
            "RB2 Rd, Sion West, Mumbai, Maharashtra 400022",
            "022-24076381", "022-24063000", 19.0360, 72.8600,
            Arrays.asList("Disaster & Mass Casualty Ward", "Trauma & Fracture Unit", "Burn & Plastic Surgery", "Paediatric Emergency"),
            Arrays.asList("FLOOD", "BRIDGE_COLLAPSE", "FIRE"), 1400, true
        ));

        hospitals.add(new HospitalDTO(
            104, "Breach Candy Hospital", "South Mumbai", "Breach Candy",
            "60A, Bhulabhai Desai Marg, Breach Candy, Cumballa Hill, Mumbai 400026",
            "022-23667788", "022-23667000", 18.9715, 72.8055,
            Arrays.asList("24x7 Casualty & Emergency", "Cardiac Emergency", "Intensive Care Unit"),
            Arrays.asList("FIRE", "BRIDGE_COLLAPSE"), 212, true
        ));

        hospitals.add(new HospitalDTO(
            105, "BYL Nair Charitable Hospital", "South Mumbai", "Mumbai Central",
            "Dr AL Nair Rd, Near Mumbai Central Station, Mumbai 400008",
            "022-23027000", "022-23081418", 18.9723, 72.8228,
            Arrays.asList("Toxicology & Chemical Poisoning", "Major Trauma Center", "Emergency Resuscitation"),
            Arrays.asList("CHEMICAL_LEAK", "BRIDGE_COLLAPSE", "FLOOD"), 1300, true
        ));

        hospitals.add(new HospitalDTO(
            106, "Cooper Hospital (HBT Medical College)", "Bandra & Western Suburbs", "Vile Parle West",
            "U 15, Juhu Scheme, Vile Parle West, Mumbai 400056",
            "022-26207254", "022-26207256", 19.1085, 72.8360,
            Arrays.asList("Level-2 Trauma Center", "Flood Emergency Response", "General Surgery"),
            Arrays.asList("FLOOD", "BRIDGE_COLLAPSE"), 600, true
        ));

        hospitals.add(new HospitalDTO(
            107, "Kokilaben Dhirubhai Ambani Hospital", "Bandra & Western Suburbs", "Andheri West",
            "Rao Saheb Achutrao Patwardhan Marg, Four Bungalows, Andheri West, Mumbai 400053",
            "022-42696969", "022-30999999", 19.1310, 72.8250,
            Arrays.asList("24x7 Critical Care & Trauma", "Advanced Burn Unit", "Stroke & Neuro Emergency", "Air Ambulance"),
            Arrays.asList("FIRE", "CHEMICAL_LEAK", "BRIDGE_COLLAPSE"), 750, true
        ));

        hospitals.add(new HospitalDTO(
            108, "Fortis Hospital Mulund", "Eastern Suburbs & Thane", "Mulund West",
            "Mulund - Goregaon Link Rd, Bhandup West, Mumbai 400078",
            "022-67994444", "022-67994100", 19.1620, 72.9480,
            Arrays.asList("24x7 Trauma & Emergency", "Cardiac Resuscitation", "ICU & Ventilator Care"),
            Arrays.asList("BRIDGE_COLLAPSE", "FLOOD", "FIRE"), 315, true
        ));

        hospitals.add(new HospitalDTO(
            109, "Bombay Hospital & Medical Research Centre", "South Mumbai", "Marine Lines",
            "12, Vitthaldas Thackersey Marg, New Marine Lines, Marine Lines, Mumbai 400020",
            "022-22067676", "022-22082121", 18.9410, 72.8280,
            Arrays.asList("Emergency Casualty Ward", "Polytrauma Unit", "Neurosurgery ICU"),
            Arrays.asList("BRIDGE_COLLAPSE", "FIRE"), 830, true
        ));

        hospitals.add(new HospitalDTO(
            110, "Kasturba Hospital for Infectious Diseases", "Central Mumbai", "Chinchpokli",
            "Sane Guruji Marg, Arthur Road, Chinchpokli, Mumbai 400011",
            "022-23083901", "022-23092424", 18.9880, 72.8310,
            Arrays.asList("Specialised Isolation & Biological Hazard", "Chemical Contamination Care", "Epidemic Emergency"),
            Arrays.asList("CHEMICAL_LEAK", "FLOOD"), 500, true
        ));

        hospitals.add(new HospitalDTO(
            111, "KB Bhabha Hospital Kurla", "Central Mumbai", "Kurla West",
            "Belgrami Rd, Near Railway Station, Kurla West, Mumbai 400070",
            "022-26500241", "022-26500244", 19.0685, 72.8790,
            Arrays.asList("Flood Inundation Emergency Ward", "Casualty & First Aid", "Mithi River Disaster Response"),
            Arrays.asList("FLOOD", "BRIDGE_COLLAPSE"), 300, true
        ));

        hospitals.add(new HospitalDTO(
            112, "Jupiter Hospital Thane", "Eastern Suburbs & Thane", "Thane West",
            "Eastern Express Highway, Next to Viviana Mall, Thane West, Maharashtra 400601",
            "022-21725555", "022-21725656", 19.2020, 72.9660,
            Arrays.asList("Level-1 Regional Trauma Center", "Burn ICU", "Multi-organ ICU", "24x7 Ambulance"),
            Arrays.asList("FIRE", "BRIDGE_COLLAPSE", "CHEMICAL_LEAK", "FLOOD"), 350, true
        ));
    }

    private void initGuides() {
        guides.put("FLOOD", new DisasterProtectionGuideDTO(
            "FLOOD", "Monsoon Waterlogging & Flash Flood Safety", "🌊",
            "Mumbai floods advance rapidly due to high tide and heavy downpours. Protect yourself from hidden open manholes, submerged electrical current, and contaminated water.",
            Arrays.asList(
                "Immediately move to the 2nd floor or higher elevation in a structurally solid building.",
                "Turn off your electrical main switch to prevent deadly shocks from submerged wiring.",
                "Do NOT wade through standing water — open BMC manholes are completely invisible below surface.",
                "Keep emergency bag (water, flashlight, dry snacks, power bank) close at hand."
            ),
            Arrays.asList(
                new FirstAidStep("Drowning & Water Inhalation CPR", "Place victim on back, tilt head back, and begin 30 chest compressions followed by 2 rescue breaths. Repeat until medical help arrives.", "Do NOT press stomach to force water out; focus on CPR breathing."),
                new FirstAidStep("Submerged Wire Electrocution", "Do NOT touch the victim directly with bare hands while they are in water. Turn off main power grid or use a dry wooden stick/plastic pole to disconnect.", "Always assume standing floodwater near light poles is energized."),
                new FirstAidStep("Hypothermia & Waterborne Infection", "Remove wet clothes immediately. Wrap victim in dry blankets/towels. Disinfect cuts or scrapes with antiseptics to prevent Leptospirosis.", "Avoid direct contact with Mithi River or gutter overflow water.")
            ),
            Arrays.asList(
                "Move up to 2nd storey or higher elevation immediately",
                "Keep 1916 (BMC Control Room) and 108 saved in speed dial",
                "Carry dry snacks, drinking water bottle, and medicines in plastic bags",
                "Unplug all heavy home appliances before water enters premise"
            ),
            Arrays.asList(
                "Do NOT attempt to drive through flooded underpasses (e.g., Sion / Andheri subway)",
                "Do NOT wade near electrical poles, transformers, or street lights",
                "Do NOT leave children unattended near waterlogged streets",
                "Do NOT drink unboiled tap water after flood inundation"
            ),
            Arrays.asList(
                "Pack 2 Litres of clean drinking water per person",
                "Waterproof zipper bag for Aadhaar, passport, essential documents",
                "High-intensity LED torch flashlight + extra batteries",
                "First Aid kit (Band-Aids, Dettol antiseptic, ORS sachets, paracetamol)",
                "Fully charged mobile phone + power bank (20,000 mAh recommended)"
            )
        ));

        guides.put("FIRE", new DisasterProtectionGuideDTO(
            "FIRE", "Building Fire & Smoke Inhalation Safety", "🔥",
            "Structural fires in high-rises or congested urban areas spread smoke faster than flames. Oxygen deprivation occurs within 90 seconds.",
            Arrays.asList(
                "Crawl low under smoke — clean, breathable air stays within 30cm of the floor.",
                "Test doors with the BACK of your hand before opening. If hot, do NOT open.",
                "Do NOT use elevators under any circumstances. Take fire refuge balconies or stairwells.",
                "If trapped in a room, seal door gaps with wet towels or bedsheets to block toxic smoke."
            ),
            Arrays.asList(
                new FirstAidStep("Smoke Inhalation Rescue", "Move victim to fresh air instantly. Loosen tight clothing around neck. If breathing is shallow or stopped, start CPR.", "Do NOT administer liquids if victim is unconscious."),
                new FirstAidStep("Thermal Burn Immediate Care", "Cool burns immediately under cool running tap water for 15 to 20 minutes. Cover loosely with sterile non-stick bandage or clean dry cloth.", "Do NOT apply ice, butter, toothpaste, or oil on burnt skin."),
                new FirstAidStep("Stop, Drop & Roll (Clothing on Fire)", "If your clothes catch fire: Stop moving, Drop to the ground, and Roll over repeatedly while covering face with hands.", "Do NOT run — running fans the flames and accelerates burning.")
            ),
            Arrays.asList(
                "Crawl low on hands and knees under toxic smoke",
                "Feel door handles with back of hand before opening",
                "Cover nose and mouth with a thick wet cloth or mask",
                "Signal from windows using bright cloth or phone flashlight for fire brigade"
            ),
            Arrays.asList(
                "Do NOT take elevators or lifts during a fire alarm",
                "Do NOT open hot doors or doors where smoke is billowing underneath",
                "Do NOT re-enter a burning building to retrieve personal belongings",
                "Do NOT break windows unless absolutely necessary for ventilation"
            ),
            Arrays.asList(
                "Thick cotton towels (wet for smoke filter)",
                "Heavy-duty leather or heat-resistant work gloves",
                "Whistle or high-decibel alarm for signaling rescuers",
                "Emergency burn ointment (Burnol / Silver Sulfadiazine) & sterile gauze",
                "N95 / Smoke respirator masks"
            )
        ));

        guides.put("BRIDGE_COLLAPSE", new DisasterProtectionGuideDTO(
            "BRIDGE_COLLAPSE", "Structural & Infrastructure Collapse Safety", "🌉",
            "Bridge or building structural failure causes massive debris, crushing injuries, and dust inhalation. Fast stabilization saves lives.",
            Arrays.asList(
                "If inside a collapsing structure: Drop, Cover, and Hold On under a heavy desk or interior load-bearing pillar.",
                "Protect your head and neck with arms, pillows, or heavy clothing.",
                "If trapped under debris: cover nose with cloth, tap rhythmically on metal pipes so rescuers locate you.",
                "Stay clear of dangling concrete slabs, snapped power cables, and unstable edges."
            ),
            Arrays.asList(
                new FirstAidStep("Crush Injury & Heavy Bleeding", "Apply direct firm pressure to bleeding wounds using sterile cloth or pressure bandage. Elevate injured limb if no fracture suspected.", "Do NOT remove impaled objects from body; stabilize around the object."),
                new FirstAidStep("Fracture & Spinal Immobilization", "Keep injured limb completely immobilized. If spinal injury is suspected, do NOT move the patient unless immediate fire or explosion threatens life.", "Avoid twisting or turning victim's neck or back."),
                new FirstAidStep("Dust Inhalation & Airway Clearance", "Clear dust/debris from mouth and nose. Place victim in lateral recovery position if breathing.", "Do NOT offer food or water if surgery may be required soon.")
            ),
            Arrays.asList(
                "Drop, Cover, and Hold On under heavy structural frame",
                "Tap on pipes or solid walls in sets of three (SOS rhythm) for rescue sonar",
                "Cover nose with cloth to prevent suffocation from fine silica dust",
                "Keep clear of overhead powerlines near collapse site"
            ),
            Arrays.asList(
                "Do NOT use lighters or matches near collapse zone (gas leaks may cause explosions)",
                "Do NOT crowd near unstable debris edges where secondary collapse can occur",
                "Do NOT pull heavily trapped victims recklessly without trained NDRF support",
                "Do NOT shout continuously — conserve oxygen while trapped"
            ),
            Arrays.asList(
                "Heavy-duty work gloves and protective dust goggles",
                "Emergency whistle for signalling search teams",
                "Tourniquets, elastic compression bandages, and splints",
                "High-energy protein bars and water sachets",
                "Multi-tool / Swiss knife and duct tape"
            )
        ));

        guides.put("CHEMICAL_LEAK", new DisasterProtectionGuideDTO(
            "CHEMICAL_LEAK", "Industrial Hazard & Toxic Gas Release Safety", "☣️",
            "Industrial chemical releases (ammonia, chlorine, LPG, industrial solvents) create toxic atmospheric plumes. Evacuate CROSSWIND, not downwind.",
            Arrays.asList(
                "Determine wind direction and immediately move PERPENDICULAR (crosswind) to the chemical gas plume.",
                "Cover mouth and nose with a damp towel, wet cloth, or activated carbon mask.",
                "If shelter-in-place is instructed: go indoors, shut all doors/windows, turn off AC units and exhaust fans, seal gaps with damp towels.",
                "If exposed to chemical liquid/gas: strip contaminated clothes immediately and flush eyes/skin under clean running water for 15+ minutes."
            ),
            Arrays.asList(
                new FirstAidStep("Chemical Eye & Skin Decontamination", "Flush exposed eyes and skin with continuous clean water for at least 15 to 20 minutes. Remove contaminated clothing while under shower.", "Do NOT rub eyes or apply neutralizing chemicals without toxicological advice."),
                new FirstAidStep("Toxic Inhalation First Aid", "Move patient immediately to fresh air outside the plume. Keep victim calm in a semi-upright seated position to ease breathing.", "Administer 100% medical oxygen if trained personnel available."),
                new FirstAidStep("LPG / Flammable Gas Protocol", "If gas smell is present: turn off main gas cylinder valve. Do NOT flip electric switches (on or off) or create any spark.", "Do NOT start motor vehicles or light matches in gas zone.")
            ),
            Arrays.asList(
                "Move crosswind (perpendicular to wind direction) away from gas cloud",
                "Cover face with wet cotton towel to absorb water-soluble gases (like Ammonia & Chlorine)",
                "Seal all doors, windows, and AC vents with duct tape or damp sheets if trapped indoors",
                "Strip contaminated clothes immediately and wash skin with plenty of clean water"
            ),
            Arrays.asList(
                "Do NOT run downwind (in the exact direction the gas cloud is blowing)",
                "Do NOT flip light switches, use lighters, or operate phones near flammable gas leaks",
                "Do NOT touch spilled liquid chemicals or walk through chemical puddles",
                "Do NOT re-enter contaminated area until cleared by Disaster Management Authorities"
            ),
            Arrays.asList(
                "Full-face gas mask or N95 carbon respirator masks",
                "Safety goggles (non-vented) to protect eyes from chemical fumes",
                "Duct tape and heavy plastic sheeting for room sealing",
                "Saline eye wash bottles and antiseptic skin cleansers",
                "Bottled drinking water (minimum 3 Litres)"
            )
        ));
    }

    public List<HospitalDTO> getAllHospitals() {
        return hospitals;
    }

    public List<HospitalDTO> getHospitalsByFilter(String disasterType, String region) {
        return hospitals.stream()
            .filter(h -> {
                boolean matchDisaster = (disasterType == null || disasterType.isBlank() || disasterType.equalsIgnoreCase("ALL"))
                    || (h.getRelevantDisasters() != null && h.getRelevantDisasters().contains(disasterType.toUpperCase()));
                boolean matchRegion = (region == null || region.isBlank() || region.equalsIgnoreCase("ALL"))
                    || (h.getRegion() != null && h.getRegion().equalsIgnoreCase(region.trim()));
                return matchDisaster && matchRegion;
            })
            .collect(Collectors.toList());
    }

    public DisasterProtectionGuideDTO getGuide(String type) {
        if (type == null) return guides.get("FLOOD");
        DisasterProtectionGuideDTO guide = guides.get(type.toUpperCase());
        return guide != null ? guide : guides.get("FLOOD");
    }

    public Map<String, DisasterProtectionGuideDTO> getAllGuides() {
        return guides;
    }
}
