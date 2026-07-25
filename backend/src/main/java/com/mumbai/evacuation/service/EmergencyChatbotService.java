package com.mumbai.evacuation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mumbai.evacuation.disaster.DisasterEvent;
import com.mumbai.evacuation.dto.ChatRequest;
import com.mumbai.evacuation.dto.ChatResponse;
import com.mumbai.evacuation.model.Shelter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Emergency AI Assistant Service for Mumbai Disaster Evacuation.
 *
 * Provides real-time context-aware safety guidance, evacuation corridor recommendations,
 * and emergency helpline assistance powered by Google Gemini API.
 *
 * The system prompt is deliberately detailed and Mumbai-specific so Gemini gives
 * practical, actionable advice — not generic text-book safety tips.
 */
@Service
public class EmergencyChatbotService {

    @Autowired
    private GraphService graphService;

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.provider:gemini}")
    private String provider;

    @Value("${llm.gemini-model:gemini-1.5-flash}")
    private String geminiModel;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatResponse processChatQuery(ChatRequest request) {
        String userQuery = request.getMessage() != null ? request.getMessage().trim() : "";
        if (userQuery.isEmpty()) {
            return new ChatResponse("Please ask any safety question or request evacuation help for Mumbai.", false, getDefaultSuggestedActions());
        }

        Collection<DisasterEvent> disasters = graphService.getActiveDisasters();
        boolean activeDisastersPresent = !disasters.isEmpty();

        // 1. Build live system context
        String systemContext = buildLiveSystemContext(disasters);

        // 2. Try calling Gemini API if API key is present
        if (apiKey != null && !apiKey.isBlank() && !apiKey.contains("your_gemini_api_key")) {
            try {
                String aiReply = callGeminiApi(systemContext, userQuery);
                if (aiReply != null && !aiReply.isBlank()) {
                    return new ChatResponse(aiReply, activeDisastersPresent, buildSuggestedActions(userQuery, activeDisastersPresent));
                }
            } catch (Exception e) {
                System.err.println("[EmergencyChatbotService] Gemini API call failed: " + e.getMessage());
            }
        }

        // 3. Fallback response generator (ensures 100% uptime even if API quota or internet fails)
        String fallbackReply = generateFallbackSafetyReply(userQuery, disasters);
        return new ChatResponse(fallbackReply, activeDisastersPresent, buildSuggestedActions(userQuery, activeDisastersPresent));
    }

    /**
     * Builds a rich, detailed system prompt sent to Gemini before every user query.
     *
     * The prompt is intentionally very specific to Mumbai geography, known flood
     * hotspots, evacuation corridors, and disaster scenarios so that Gemini can
     * produce concrete, locally-relevant advice rather than generic safety text.
     */
    private String buildLiveSystemContext(Collection<DisasterEvent> disasters) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== ROLE ===\n");
        sb.append("You are the official AI Emergency Advisor for the Mumbai Metropolitan Region (MMR) Disaster Evacuation System. ");
        sb.append("You are calm, authoritative, and hyper-practical. You give life-saving advice that is:\n");
        sb.append("- Specific to Mumbai's geography, infrastructure, and monsoon patterns\n");
        sb.append("- Actionable within minutes, not hours\n");
        sb.append("- Prioritised by what saves lives first\n");
        sb.append("- Never vague or generic. Always concrete.\n\n");

        sb.append("=== RESPONSE FORMAT ===\n");
        sb.append("Always structure your reply with:\n");
        sb.append("1. A bold disaster-type header with an emoji (e.g. ### 🌊 Flood Emergency Response)\n");
        sb.append("2. An urgent 1-sentence situation summary\n");
        sb.append("3. IMMEDIATE ACTIONS (numbered, do within 0-5 minutes)\n");
        sb.append("4. EVACUATION GUIDANCE (specific Mumbai routes and corridors)\n");
        sb.append("5. DO NOT DO (common deadly mistakes people make in this disaster)\n");
        sb.append("6. CALL NOW section with relevant Mumbai helplines only\n");
        sb.append("Keep total response under 350 words. Use **bold** for critical points.\n\n");

        sb.append("=== MUMBAI GEOGRAPHY & FLOOD HOTSPOTS ===\n");
        sb.append("Known extreme flood zones (avoid during heavy rain):\n");
        sb.append("- Sion Circle, Milan Subway, Andheri Subway, Hindmata Junction, Kings Circle\n");
        sb.append("- Kurla LBS Marg underpasses, Malad subway, Dahisar check naka\n");
        sb.append("- Mithi River banks (Kurla, Kalina, BKC periphery) — rises extremely fast in heavy rain\n");
        sb.append("- Dharavi low-lying areas, Parel (BPT colony), Bandra Reclamation area\n\n");
        sb.append("Relatively safer elevated corridors:\n");
        sb.append("- Western Express Highway (WEH) — elevated, drains faster\n");
        sb.append("- Eastern Express Highway (EEH) — elevated sections near Ghatkopar\n");
        sb.append("- Coastal Road (sea-link side) — modern drainage\n");
        sb.append("- SV Road (moderate), LBS Marg (moderate)\n\n");

        sb.append("=== MUMBAI DISASTER PLAYBOOKS ===\n");
        sb.append("FLOOD:\n");
        sb.append("- If water is knee-deep or rising: DO NOT DRIVE. Abandon vehicle, move to nearest building's 2nd floor or above.\n");
        sb.append("- Manholes open in floods — water surface looks uniform but manholes are invisible. Do NOT wade.\n");
        sb.append("- Electric poles/wires in water = lethal. Stay 10+ metres from any downed wire.\n");
        sb.append("- If trapped at home: shift all valuables/medicines/documents to highest floor. Signal from window.\n");
        sb.append("- Mithi River flood spreads to BKC and Kalina within 30-45 minutes of a cloudburst.\n\n");
        sb.append("FIRE:\n");
        sb.append("- Mumbai buildings often have blocked stairwells with stored goods — check for alternative exit.\n");
        sb.append("- Feel the door with back of hand before opening. If hot, do NOT open — fire is on the other side.\n");
        sb.append("- Close all doors between you and fire — a closed door buys 30+ minutes.\n");
        sb.append("- Wet cloth over mouth+nose reduces smoke inhalation significantly.\n");
        sb.append("- Signal from window; don't jump unless fire is directly below and it's single storey.\n\n");
        sb.append("CHEMICAL/GAS LEAK:\n");
        sb.append("- Chembur, Trombay, Mahul are high-risk industrial zones (Bharat Petroleum, RCF, HPCL).\n");
        sb.append("- Move crosswind (not upwind, not downwind — perpendicular to wind direction).\n");
        sb.append("- If you smell rotten eggs: LPG leak. Turn off main valve, open all windows, leave immediately, no switches/lighters.\n");
        sb.append("- Shelter-in-place means sealing room gaps with wet towels, switching off ACs and ventilation.\n\n");
        sb.append("BRIDGE/STRUCTURE COLLAPSE:\n");
        sb.append("- Drop, Cover, Hold On under a table or against an interior wall — not near windows or exterior walls.\n");
        sb.append("- If trapped in rubble: tap on pipes or walls rhythmically — rescuers detect this. Conserve voice.\n");
        sb.append("- Do NOT re-enter a damaged building for any reason.\n\n");

        sb.append("=== MUMBAI EMERGENCY HELPLINES ===\n");
        sb.append("- BMC Disaster Control Room: 1916 (24×7, best first call)\n");
        sb.append("- Medical Emergency / Ambulance: 108\n");
        sb.append("- Police: 100 / 112\n");
        sb.append("- Fire Brigade: 101\n");
        sb.append("- NDRF (National Disaster Response Force): 011-24363260\n");
        sb.append("- Mumbai Railway Emergency: 1512\n");
        sb.append("- Poison Control (KEM Hospital): 022-24107687\n\n");

        sb.append("=== LIVE SYSTEM STATE ===\n");
        if (disasters.isEmpty()) {
            sb.append("Active Disasters: NONE currently reported. All major corridors are operational.\n");
        } else {
            sb.append("⚠️ ACTIVE INCIDENTS RIGHT NOW (" + disasters.size() + " event(s)):\n");
            for (DisasterEvent d : disasters) {
                sb.append("  • " + d.getType() + ": " + d.getDescription()
                        + " | Affected radius: " + (int) d.getAffectedRadiusMeters() + "m"
                        + " | Roads blocked: " + (d.isBlockRoads() ? "YES" : "No (heavy congestion)") + "\n");
            }
        }

        Collection<Shelter> shelters = graphService.getShelterService().getAllShelters();
        long openShelters = shelters.stream().filter(s -> !s.isFull()).count();
        int totalAvailable = shelters.stream().mapToInt(Shelter::getRemainingCapacity).sum();
        sb.append("Evacuation Shelters: " + openShelters + "/" + shelters.size()
                + " open, " + totalAvailable + " total spots available.\n\n");

        sb.append("=== TASK ===\n");
        sb.append("The user below is in or near an emergency situation. Give them immediately actionable, ");
        sb.append("Mumbai-specific, life-prioritised guidance. Do not add unnecessary caveats. Be direct.\n");

        return sb.toString();
    }

    private String callGeminiApi(String systemContext, String userQuery) throws Exception {
        String[] candidateModels = new String[]{
            geminiModel,
            "gemini-2.0-flash",
            "gemini-1.5-flash-latest",
            "gemini-1.5-flash",
            "gemini-pro"
        };

        String combinedPrompt = systemContext + "\n\nUser Message: " + userQuery;

        Map<String, Object> requestBody = new HashMap<>();
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> contentPart = new HashMap<>();
        List<Map<String, String>> parts = new ArrayList<>();

        Map<String, String> textMap = new HashMap<>();
        textMap.put("text", combinedPrompt);
        parts.add(textMap);

        contentPart.put("parts", parts);
        contents.add(contentPart);
        requestBody.put("contents", contents);

        // Tell Gemini to be focused and concise — matches our response format instruction
        Map<String, Object> genConfig = new HashMap<>();
        genConfig.put("temperature", 0.3);   // low temperature = more precise, less hallucination
        genConfig.put("maxOutputTokens", 600);
        requestBody.put("generationConfig", genConfig);

        String jsonPayload = objectMapper.writeValueAsString(requestBody);

        for (String model : candidateModels) {
            if (model == null || model.isBlank()) continue;
            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + model.trim() + ":generateContent?key=" + apiKey.trim();

            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode textNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
                    if (!textNode.isMissingNode()) {
                        return textNode.asText();
                    }
                } else {
                    System.err.println("[EmergencyChatbotService] Model " + model + " returned HTTP " + response.statusCode() + ", trying next candidate...");
                }
            } catch (Exception e) {
                System.err.println("[EmergencyChatbotService] Error calling model " + model + ": " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Offline fallback — rich, practical, Mumbai-specific advice per disaster type.
     * Triggered only when Gemini API is unavailable.
     */
    private String generateFallbackSafetyReply(String query, Collection<DisasterEvent> disasters) {
        String q = query.toLowerCase();

        if (q.contains("flood") || q.contains("rain") || q.contains("water") || q.contains("waterlog") || q.contains("sion") || q.contains("mithi")) {
            return "### 🌊 Flood Emergency — Act NOW\n\n" +
                    "Mumbai floods move fast. Every minute matters.\n\n" +
                    "**IMMEDIATE ACTIONS (next 5 minutes):**\n" +
                    "1. **If water is at ankle level or rising** — stop moving and go UP. Reach the 2nd floor or any higher ground immediately.\n" +
                    "2. **Turn off your electricity main switch** at the fuse board — submerged wires cause electrocution.\n" +
                    "3. **Do NOT wade through water** — open manholes are invisible and there are live electrical wires. Just one step can be fatal.\n" +
                    "4. **If driving** — abandon your vehicle if water reaches the door sill. A running car engine can stall and trap you.\n" +
                    "5. **Grab your essentials** in under 2 minutes: medicines, phone (charged), ID, cash in a waterproof bag.\n\n" +
                    "**AVOID THESE DEATH TRAPS IN MUMBAI:**\n" +
                    "- ❌ Sion Circle, Milan Subway, Andheri Subway — flood within 30 mins of a cloudburst\n" +
                    "- ❌ Mithi River areas (Kurla, Kalina, BKC edges) — river overflows without warning\n" +
                    "- ❌ Dharavi lanes and Hindmata junction — extremely low-lying\n\n" +
                    "**SAFER ROUTES:**\n" +
                    "- ✅ Western Express Highway (elevated, drains faster)\n" +
                    "- ✅ Eastern Express Highway (elevated near Ghatkopar)\n" +
                    "- ✅ Coastal Road (modern drainage)\n\n" +
                    "**CALL NOW:** BMC Control Room **1916** | Ambulance **108** | Police **100**\n\n" +
                    "📍 *Use the Live Route Planner on this map to find the nearest open shelter and a dry evacuation corridor.*";

        } else if (q.contains("fire") || q.contains("smoke") || q.contains("burn") || q.contains("bkc fire") || q.contains("building fire")) {
            return "### 🔥 Building Fire — Immediate Evacuation Protocol\n\n" +
                    "Every 30 seconds counts. Do NOT wait for instructions from others.\n\n" +
                    "**IMMEDIATE ACTIONS:**\n" +
                    "1. **Feel the door** with the back of your hand before opening — if hot, DO NOT OPEN. Fire is directly outside.\n" +
                    "2. **Close all doors** between you and the fire — a closed door buys you 30+ minutes of survival time.\n" +
                    "3. **Get LOW under smoke** — crawl on your hands and knees. Air at floor level is significantly cleaner.\n" +
                    "4. **Wet cloth over mouth and nose** — any fabric soaked in water reduces smoke inhalation dramatically.\n" +
                    "5. **Use stairwells ONLY** — NEVER take the elevator. Power cuts trap people.\n\n" +
                    "**IF YOU'RE TRAPPED:**\n" +
                    "- Seal door gaps with wet clothing to stop smoke entering\n" +
                    "- Signal from the window — wave something bright\n" +
                    "- Call 101 and give your floor and flat number\n" +
                    "- Do NOT jump unless fire is directly below and you are on ground or 1st floor\n\n" +
                    "**MUMBAI NOTE:** Many Mumbai buildings have stairwells blocked by stored goods. Check your alternate exit route NOW, before you need it.\n\n" +
                    "**CALL NOW:** Fire Brigade **101** | Ambulance **108** | Police **100**";

        } else if (q.contains("chemical") || q.contains("gas") || q.contains("leak") || q.contains("lpg") || q.contains("chembur") || q.contains("trombay") || q.contains("mahul")) {
            return "### ☣️ Gas / Chemical Leak — Evacuate Smart, Not Fast\n\n" +
                    "Running in the wrong direction makes it worse. Direction matters more than speed.\n\n" +
                    "**IMMEDIATE ACTIONS:**\n" +
                    "1. **Determine wind direction** — feel the air on your face or check which way leaves/flags are moving.\n" +
                    "2. **Move CROSSWIND** — perpendicular (sideways) to the wind, not directly away. This gets you out of the plume fastest.\n" +
                    "3. **Cover your nose and mouth** — wet cloth, N95 mask, or even a dry T-shirt over the face helps.\n" +
                    "4. **If it smells like rotten eggs (LPG/gas):** Do NOT flip any switches, use a lighter, or create sparks — evacuate and leave the door open behind you.\n" +
                    "5. **Get at least 500 metres away** from the source before stopping.\n\n" +
                    "**IF YOU CANNOT EVACUATE (Shelter-in-Place):**\n" +
                    "- Seal door and window gaps with wet towels\n" +
                    "- Turn off ALL ACs, fans, and ventilation — they pull contaminated air in\n" +
                    "- Stay at floor level (most toxic gases are lighter than air and accumulate at ceiling height)\n\n" +
                    "**HIGH-RISK ZONES IN MUMBAI:** Chembur, Trombay, Mahul (HPCL/BPCL/RCF industrial belt)\n\n" +
                    "**CALL NOW:** BMC Disaster Control **1916** | Fire (HazMat) **101** | Ambulance **108** | Poison Control **022-24107687**";

        } else if (q.contains("bridge") || q.contains("collapse") || q.contains("earthquake") || q.contains("building fall") || q.contains("structure")) {
            return "### 🏗️ Structure Collapse / Earthquake Response\n\n" +
                    "**DURING the shaking / collapse:**\n" +
                    "1. **DROP, COVER, HOLD ON** — get under a sturdy table or against an interior wall. Stay away from windows, exterior walls, and heavy furniture.\n" +
                    "2. **Do NOT run outside** during shaking — most injuries happen from falling glass and debris at building exits.\n" +
                    "3. **If in a vehicle** — stop away from bridges and overpasses, stay inside.\n\n" +
                    "**IF TRAPPED IN RUBBLE:**\n" +
                    "- **Do NOT shout continuously** — you'll inhale dust and exhaust your voice.\n" +
                    "- **Tap on pipes or walls rhythmically (3 taps, pause, 3 taps)** — rescue teams listen for this pattern.\n" +
                    "- Cover your nose and mouth with clothing to filter dust.\n" +
                    "- Move as little as possible to avoid shifting debris.\n\n" +
                    "**AFTER:**\n" +
                    "- Do NOT re-enter any building with visible cracks, tilting, or fallen sections — aftershocks are likely.\n" +
                    "- Check for gas leaks: smell + do NOT use switches.\n" +
                    "- Move to open ground and await NDRF team instructions.\n\n" +
                    "**CALL NOW:** NDRF **011-24363260** | Police **112** | Ambulance **108** | BMC **1916**";

        } else if (q.contains("storm") || q.contains("cyclone") || q.contains("lightning") || q.contains("wind")) {
            return "### 🌀 Severe Storm / Cyclone Safety — Mumbai\n\n" +
                    "**IMMEDIATE ACTIONS:**\n" +
                    "1. **Get indoors now** — reinforced concrete buildings are safe; avoid old structures, chawls with tin/asbestos roofs.\n" +
                    "2. **Stay away from windows and glass doors** — strong winds turn glass into projectiles.\n" +
                    "3. **Unplug all electronics** — lightning can travel through electrical circuits.\n" +
                    "4. **If caught outdoors in lightning:** Crouch low, feet together, do NOT lie flat. Stay away from trees, poles, and open water.\n" +
                    "5. **Secure loose objects** on balconies — potted plants, chairs become dangerous projectiles at 80+ km/h winds.\n\n" +
                    "**MUMBAI-SPECIFIC:** During pre-cyclone conditions, sea walls at Marine Drive, Worli Sea Face, and Bandra Bandstand are extremely dangerous. Stay 50+ metres back.\n\n" +
                    "**CALL NOW:** BMC **1916** | Coastguard (if near sea) **1554** | Police **100**";

        } else if (q.contains("number") || q.contains("helpline") || q.contains("contact") || q.contains("call")) {
            return "### 📞 Mumbai Emergency Helplines — Save These NOW\n\n" +
                    "| Service | Number |\n" +
                    "|---|---|\n" +
                    "| BMC Disaster Control (best first call) | **1916** |\n" +
                    "| Ambulance / Medical Emergency | **108** |\n" +
                    "| Police | **100 / 112** |\n" +
                    "| Fire Brigade | **101** |\n" +
                    "| NDRF (for collapse/major disaster) | **011-24363260** |\n" +
                    "| Mumbai Railway Emergency | **1512** |\n" +
                    "| Poison Control (KEM Hospital) | **022-24107687** |\n" +
                    "| Coastguard (marine emergency) | **1554** |\n\n" +
                    "📌 *BMC's 1916 operates 24×7 and can dispatch rescue, ambulance, and fire simultaneously.*";

        } else if (q.contains("shelter") || q.contains("safe place") || q.contains("where to go") || q.contains("camp")) {
            Collection<Shelter> shelters = graphService.getShelterService().getAllShelters();
            long open = shelters.stream().filter(s -> !s.isFull()).count();
            int spots = shelters.stream().mapToInt(Shelter::getRemainingCapacity).sum();
            return "### ⛺ Evacuation Shelter Information\n\n" +
                    "**" + open + " of " + shelters.size() + " shelters are currently OPEN** with **" + spots + " total spots available**.\n\n" +
                    "**What to expect at a shelter:**\n" +
                    "- BMC provides water, basic food (khichdi/dal), and first-aid\n" +
                    "- Blankets and sleeping mats are provided at major shelters\n" +
                    "- Keep your Aadhaar card or any ID ready for registration\n\n" +
                    "**Major Shelter Locations:**\n" +
                    "- 🏫 BMC Schools across all wards (largest network)\n" +
                    "- 🏟️ Dadar Sports Complex — central Mumbai\n" +
                    "- 🏢 BKC Exhibition Centre — for Kurla/Sion flood zone evacuees\n" +
                    "- 🏥 Cooper Hospital Compound — Vile Parle West\n" +
                    "- 🌳 Borivali National Park peripheral zones — North Mumbai\n\n" +
                    "📍 *Click the **Shelters** tab in the sidebar to see live occupancy and get a direct evacuation route to the nearest open shelter.*";

        } else {
            return "### 🚨 Mumbai Emergency AI Assistant\n\n" +
                    "I provide **specific, practical, life-saving guidance** for Mumbai's emergency situations.\n\n" +
                    "**Ask me about:**\n" +
                    "- 🌊 **Flooding** — what to do right now if water is rising near you\n" +
                    "- 🔥 **Building Fire** — how to evacuate safely from your floor\n" +
                    "- ☣️ **Gas/Chemical Leak** — which direction to run and why\n" +
                    "- 🏗️ **Structure Collapse** — how to survive if you're trapped\n" +
                    "- 🌀 **Cyclone/Storm** — Mumbai-specific shelter precautions\n" +
                    "- 📞 **Emergency Numbers** — every helpline you might need\n" +
                    "- ⛺ **Nearest Open Shelter** — live capacity data\n\n" +
                    "**Example:** *\"There's a flood at Sion, water is at knee level, what should I do?\"*\n\n" +
                    "What is your emergency situation right now?";
        }
    }

    private List<String> getDefaultSuggestedActions() {
        return Arrays.asList(
                "🌊 Flood rising near me — help!",
                "🔥 Fire in my building",
                "☣️ Gas leak nearby",
                "📞 Emergency helplines"
        );
    }

    private List<String> buildSuggestedActions(String query, boolean activeDisastersPresent) {
        List<String> list = new ArrayList<>();
        if (activeDisastersPresent) {
            list.add("⚠️ Show safe routes around active hazards");
        }
        list.add("🌊 Water rising — what do I do?");
        list.add("🔥 Fire evacuation steps");
        list.add("⛺ Find nearest open shelter");
        list.add("📞 Emergency contact numbers");
        return list;
    }
}
