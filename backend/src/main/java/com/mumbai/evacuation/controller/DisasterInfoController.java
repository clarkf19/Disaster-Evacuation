package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.dto.DisasterProtectionGuideDTO;
import com.mumbai.evacuation.dto.HospitalDTO;
import com.mumbai.evacuation.service.DisasterInfoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

/** Static protection guides and emergency hospital directory. */
@RestController
@RequestMapping("/api/disaster-info")
public class DisasterInfoController {

    private final DisasterInfoService disasterInfoService;

    public DisasterInfoController(DisasterInfoService disasterInfoService) {
        this.disasterInfoService = disasterInfoService;
    }

    @GetMapping("/guides")
    public Map<String, DisasterProtectionGuideDTO> getAllGuides() {
        return disasterInfoService.getAllGuides();
    }

    @GetMapping("/guides/{type}")
    public DisasterProtectionGuideDTO getGuideByType(@PathVariable String type) {
        DisasterProtectionGuideDTO guide = disasterInfoService.getAllGuides().get(type.toUpperCase(Locale.ROOT));
        if (guide == null) throw new NoSuchElementException("No guide for disaster type " + type);
        return guide;
    }

    @GetMapping("/hospitals")
    public List<HospitalDTO> getHospitals(@RequestParam(required = false) String disasterType,
                                          @RequestParam(required = false) String region) {
        return disasterInfoService.getHospitalsByFilter(disasterType, region);
    }
}
