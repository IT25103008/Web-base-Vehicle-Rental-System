package com.vehiclerental.controller;

import com.vehiclerental.dao.VehicleDao;
import com.vehiclerental.dto.request.ReasonRequest;
import com.vehiclerental.dto.request.VehicleRequest;
import com.vehiclerental.dto.response.PageResponse;
import com.vehiclerental.enums.VehicleStatus;
import com.vehiclerental.model.Vehicle;
import com.vehiclerental.model.VehicleImage;
import com.vehiclerental.security.AppUserPrincipal;
import com.vehiclerental.security.BranchGuard;
import com.vehiclerental.service.AvailabilityService;
import com.vehiclerental.service.VehicleImageService;
import com.vehiclerental.service.VehicleService;
import com.vehiclerental.util.AppClock;
import com.vehiclerental.util.RentalPolicy;
import com.vehiclerental.validation.Rules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final VehicleImageService vehicleImageService;
    private final AvailabilityService availabilityService;
    private final VehicleDao vehicleDao;
    private final BranchGuard branchGuard;

    public VehicleController(VehicleService vehicleService,
                             VehicleImageService vehicleImageService,
                             AvailabilityService availabilityService,
                             VehicleDao vehicleDao,
                             BranchGuard branchGuard) {
        this.vehicleService = vehicleService;
        this.vehicleImageService = vehicleImageService;
        this.availabilityService = availabilityService;
        this.vehicleDao = vehicleDao;
        this.branchGuard = branchGuard;
    }

    // Staff/admin: full fleet list, optionally filtered by branch or status
    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public List<Vehicle> list(@RequestParam(required = false) Integer branchId,
                              @RequestParam(required = false) VehicleStatus status) {
        return vehicleService.findAll(branchId, status);
    }

    /** One page of the fleet for the console (C1). Staff default to their own branch. */
    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public PageResponse<Vehicle> page(@RequestParam(required = false) String status,
                                      @RequestParam(required = false) @Size(max = Rules.SEARCH_TEXT, message = "Search text is too long") String q,
                                      @RequestParam(required = false) Integer branchId,
                                      @RequestParam(required = false) Boolean allBranches,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size,
                                      @AuthenticationPrincipal AppUserPrincipal me) {
        Integer scope = Boolean.TRUE.equals(allBranches) ? null : branchGuard.scopeFor(me.getUserId());
        Integer branch = scope != null ? scope : branchId;
        List<Vehicle> fleet = vehicleService.findAll(branch, null);
        String text = q == null ? "" : q.trim().toLowerCase();
        List<Vehicle> matching = fleet.stream()
            .filter(v -> text.isEmpty() || (v.getModel() + " " + v.getPlateNumber() + " " + v.getCategory())
                .toLowerCase().contains(text))
            .toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (VehicleStatus s : VehicleStatus.values()) {
            counts.put(s.name(), matching.stream().filter(v -> v.getStatus() == s).count());
        }
        counts.put("all", (long) matching.size());
        List<Vehicle> filtered = matching.stream()
            .filter(v -> status == null || status.isBlank() || "all".equalsIgnoreCase(status)
                      || v.getStatus().name().equalsIgnoreCase(status))
            .toList();
        int s = PageResponse.size(size);
        int pg = PageResponse.page(page);
        List<Vehicle> items = filtered.stream().skip((long) (pg - 1) * s).limit(s).toList();
        return new PageResponse<>(items, pg, s, filtered.size(), counts);
    }

    /**
     * Public catalogue (E3): every car a customer could book at some point,
     * without choosing dates. The staff-only list above includes cars that
     * are off the road or uninsured; this one never does.
     */
    @GetMapping("/catalogue")
    public List<Vehicle> catalogue() {
        return vehicleDao.findCatalogue(AppClock.today());
    }

    // Public
    @GetMapping("/{id}")
    public Vehicle getOne(@PathVariable int id) {
        return vehicleService.findById(id);
    }

    /**
     * Public: what is free for these dates, e.g.
     *   GET /api/vehicles/search?pickup=2026-09-20&return=2026-09-23&branchId=1
     * Optional server-side refinements (E4): category, fuel, minSeats, maxRate,
     * q (model text), sort = price-asc | price-desc | newest | seats, and
     * page/size (the full count is then in the X-Total-Count header).
     */
    @GetMapping("/search")
    public ResponseEntity<List<Vehicle>> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pickup,
            @RequestParam("return") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate,
            @RequestParam(required = false) Integer branchId,
            @RequestParam(required = false) @Size(max = 40, message = "Category is too long") String category,
            @RequestParam(required = false) @Size(max = Rules.FUEL_TYPE, message = "Fuel type is too long") String fuel,
            @RequestParam(required = false) @Min(value = 1, message = "Seats must be at least 1")
                @Max(value = 60, message = "Seats can be at most 60") Integer minSeats,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "Maximum rate cannot be negative")
                @DecimalMax(value = Rules.MONEY_MAX, message = "Maximum rate is too large") BigDecimal maxRate,
            @RequestParam(required = false) @Size(max = Rules.SEARCH_TEXT, message = "Search text is too long") String q,
            @RequestParam(required = false) @Size(max = 20, message = "Unknown sort order") String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Stream<Vehicle> found = vehicleService.findAvailable(pickup, returnDate, branchId).stream();
        if (category != null && !category.isBlank()) found = found.filter(v -> category.equalsIgnoreCase(v.getCategory()));
        if (fuel != null && !fuel.isBlank()) found = found.filter(v -> fuel.equalsIgnoreCase(v.getFuelType()));
        if (minSeats != null) found = found.filter(v -> v.getPassengerCapacity() != null && v.getPassengerCapacity() >= minSeats);
        if (maxRate != null) found = found.filter(v -> v.getRentalPricePerDay().compareTo(maxRate) <= 0);
        if (q != null && !q.isBlank()) {
            String text = q.trim().toLowerCase();
            found = found.filter(v -> (v.getModel() + " " + v.getCategory() + " " + v.getFuelType()).toLowerCase().contains(text));
        }
        Comparator<Vehicle> order = switch (sort == null ? "" : sort) {
            case "price-asc" -> Comparator.comparing(Vehicle::getRentalPricePerDay);
            case "price-desc" -> Comparator.comparing(Vehicle::getRentalPricePerDay).reversed();
            case "newest" -> Comparator.comparing((Vehicle v) -> v.getManufactureYear() == null ? 0 : v.getManufactureYear()).reversed();
            case "seats" -> Comparator.comparing((Vehicle v) -> v.getPassengerCapacity() == null ? 0 : v.getPassengerCapacity()).reversed();
            default -> null;
        };
        List<Vehicle> list = (order == null ? found : found.sorted(order)).collect(Collectors.toList());
        int total = list.size();
        if (page != null) {
            int s = PageResponse.size(size);
            list = list.stream().skip((long) (PageResponse.page(page) - 1) * s).limit(s).toList();
        }
        return ResponseEntity.ok().header("X-Total-Count", String.valueOf(total)).body(list);
    }

    /**
     * Public: the days this car cannot be booked between two dates (E1), plus
     * the first free window of the requested length (E2). No customer details.
     */
    @GetMapping("/{id}/busy")
    public Map<String, Object> busy(
            @PathVariable int id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Min(value = 1, message = "Nights must be at least 1")
                @Max(value = 30, message = "A booking can be at most 30 nights") Integer nights) {
        LocalDate start = from == null || from.isBefore(AppClock.today()) ? AppClock.today() : from;
        LocalDate horizon = AppClock.today().plusDays(RentalPolicy.MAX_ADVANCE_DAYS + RentalPolicy.MAX_RENTAL_DAYS);
        LocalDate end = to == null ? start.plusDays(62) : to;
        if (end.isAfter(horizon)) end = horizon;
        if (end.isBefore(start)) throw new IllegalArgumentException("The end date must be after the start date");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("vehicleId", id);
        out.put("from", start);
        out.put("to", end);
        out.put("busy", availabilityService.busy(id, start, end));
        if (nights != null) {
            out.put("nights", nights);
            out.put("nextFree", availabilityService.nextFree(id, nights, start));
        }
        return out;
    }

    /** Public: just the first pickup date the car is free for `nights` nights (E2). */
    @GetMapping("/{id}/next-free")
    public Map<String, Object> nextFree(
            @PathVariable int id,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Nights must be at least 1")
                @Max(value = 30, message = "A booking can be at most 30 nights") int nights,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("vehicleId", id);
        out.put("nights", nights);
        out.put("nextFree", availabilityService.nextFree(id, nights, from));
        return out;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<Vehicle> create(@Valid @RequestBody VehicleRequest body,
                                          @AuthenticationPrincipal AppUserPrincipal me) {
        if (body.getBranchId() == null) {
            throw new IllegalArgumentException("Choose a home branch");
        }
        if (body.getMileage() == null) {
            throw new IllegalArgumentException("Odometer reading is required");
        }
        return ResponseEntity.ok(vehicleService.create(body.toModel(), me.getUserId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public Vehicle update(@PathVariable int id, @Valid @RequestBody VehicleRequest body,
                          @AuthenticationPrincipal AppUserPrincipal me) {
        return vehicleService.update(id, body.toModel(), me.getUserId());
    }

    // Public: the photo itself. Customers browsing the fleet need it, so this
    // is readable by anyone, exactly like GET /api/vehicles/{id}.
    // ?w=480 serves a copy scaled to that width, so a card does not download
    // the full-size original.
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable int id, @RequestParam(required = false) Integer w) {
        return vehicleImageService.find(id)
            .map(img -> serve(vehicleImageService.resized(img, w)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Photo for a vehicle. Staff take the picture at the counter, so this is
    // open to them as well as administrators.
    // No `consumes` restriction on purpose: when a mapping matches the path but
    // not the content type, Spring falls through to the static-resource handler
    // and the caller sees "No endpoint at this path", which points at entirely
    // the wrong problem. Let the request in and let the binder complain clearly.
    @PostMapping("/{id}/image")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public Vehicle uploadImage(@PathVariable int id,
                               @RequestParam(name = "file", required = false) MultipartFile file,
                               @AuthenticationPrincipal AppUserPrincipal me) {
        return vehicleImageService.store(id, file, me.getUserId());
    }

    @DeleteMapping("/{id}/image")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public Vehicle removeImage(@PathVariable int id,
                               @AuthenticationPrincipal AppUserPrincipal me) {
        return vehicleImageService.remove(id, me.getUserId());
    }

    // ---------- more photos (E5) ----------
    /** Public: the extra photos of a car, in order. */
    @GetMapping("/{id}/images")
    public List<VehicleImageService.GalleryPhoto> gallery(@PathVariable int id) {
        return vehicleImageService.gallery(id);
    }

    @GetMapping("/{id}/images/{imageId}")
    public ResponseEntity<byte[]> galleryImage(@PathVariable int id, @PathVariable int imageId,
                                               @RequestParam(required = false) Integer w) {
        return vehicleImageService.findGalleryImage(id, imageId)
            .map(img -> serve(vehicleImageService.resized(img, w)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/images")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public List<VehicleImageService.GalleryPhoto> addGalleryImage(@PathVariable int id,
            @RequestParam(name = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal AppUserPrincipal me) {
        return vehicleImageService.addToGallery(id, file, me.getUserId());
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public List<VehicleImageService.GalleryPhoto> removeGalleryImage(@PathVariable int id, @PathVariable int imageId,
                                                                   @AuthenticationPrincipal AppUserPrincipal me) {
        return vehicleImageService.removeFromGallery(id, imageId, me.getUserId());
    }

    // Staff can take a vehicle out of service / put it back
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMINISTRATOR')")
    public Vehicle changeStatus(@PathVariable int id,
                                @RequestParam VehicleStatus status,
                                @RequestParam(required = false) String reason,
                                @Valid @RequestBody(required = false) ReasonRequest body,
                                @AuthenticationPrincipal AppUserPrincipal me) {
        vehicleService.changeStatus(id, status, me.getUserId(), BookingController.reasonOf(body, reason));
        return vehicleService.findById(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public Map<String, Object> delete(@PathVariable int id,
                                      @AuthenticationPrincipal AppUserPrincipal me) {
        vehicleService.delete(id, me.getUserId());
        return Map.of("deleted", id);
    }

    private static ResponseEntity<byte[]> serve(VehicleImage img) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(img.getContentType()))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
            .body(img.getData());
    }
}
