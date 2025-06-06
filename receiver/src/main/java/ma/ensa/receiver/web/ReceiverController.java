package ma.ensa.receiver.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import ma.ensa.receiver.dto.*;
import ma.ensa.receiver.entities.Receiver;
import ma.ensa.receiver.execptions.InvalidCsvFormatException;
import ma.ensa.receiver.execptions.PhoneNumberExistsException;
import ma.ensa.receiver.execptions.ReceiverNotFoundException;
import ma.ensa.receiver.mappers.ReceiverDtoMapper;
import ma.ensa.receiver.services.ReceiverService;
import ma.ensa.receiver.utils.CsvHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.AccessDeniedException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;

@RestController
@AllArgsConstructor
@RequestMapping("/api/receivers")

public class ReceiverController {

    private final ReceiverService receiverService;
    private final CsvHelper csvHelper;

    @GetMapping("/test")
    public ResponseEntity<String> test(HttpServletRequest request){

        Collections.list(request.getHeaderNames()).forEach(System.out::println);
        return ResponseEntity.ok("test");
    }

    /**
     * Get a paginated list of receivers with optional filtering
     */
    @GetMapping
    public PageResponseDto<ReceiverDto> getReceivers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        System.out.println("hello from merge branch");
        System.out.println("Query: " + query + ", UserId: " + userId + ", Page: " + page + ", Size: " + size);

        // If trying to access other's data, restrict to own data
        if ((userId == null || !userId.equals(getCurrentUserId()))) {
            System.out.println("UserId is null or does not match current user, setting to current user id");
            userId = getCurrentUserId();
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Receiver> receiverPage = receiverService.getReceivers(query, userId, pageable);

        List<ReceiverDto> receiverDtos = receiverPage.getContent().stream()
                .map(ReceiverDtoMapper::toDto)
                .collect(toList());

        PageResponseDto<ReceiverDto> response = new PageResponseDto<>();
        response.setContent(receiverDtos);
        response.setTotalElements(receiverPage.getTotalElements());
        response.setTotalPages(receiverPage.getTotalPages());
        response.setSize(receiverPage.getSize());
        response.setNumber(receiverPage.getNumber());
        System.out.println("Response Reached here");
        System.out.println(response);

        return ResponseEntity.ok(response).getBody();
    }


    /**
     * Get a single receiver by ID
     */
    @GetMapping("/{id}")
    public ReceiverDto getReceiver(@PathVariable Long id) throws ReceiverNotFoundException, AccessDeniedException {
        Receiver receiver = receiverService.getReceiverById(id);
        verifyOwnership(receiver);
        return ReceiverDtoMapper.toDto(receiver);
    }

    /**
     * Create a new receiver
     */
    @PostMapping
    @Transactional
    public ResponseEntity<ReceiverDto> createReceiver(@Valid @RequestBody ReceiverDto receiverDto) throws PhoneNumberExistsException, AccessDeniedException {
        System.out.println("Creating receiver: " + receiverDto);
        verifyUserIdAccess(receiverDto.getUserId());
        System.out.println("UserId is valid, creating receiver");
        Receiver createdReceiver = receiverService.createReceiver(receiverDto);
        System.out.println("Receiver created: " + createdReceiver);
        return new ResponseEntity<>(ReceiverDtoMapper.toDto(createdReceiver), HttpStatus.CREATED);
    }

    /**
     * Update an existing receiver
     */
    @PutMapping("/{id}")
    public ResponseEntity<ReceiverDto> updateReceiver(
            @PathVariable Long id,
            @Valid @RequestBody ReceiverDto receiverDto) throws AccessDeniedException, PhoneNumberExistsException, ReceiverNotFoundException {

        // Verify the receiver exists and user has access to it
        Receiver existingReceiver = receiverService.getReceiverById(id);
        verifyOwnership(existingReceiver);

        // Verify user has access to set the userId
        verifyUserIdAccess(receiverDto.getUserId());

        Receiver updatedReceiver = receiverService.updateReceiver(id, receiverDto);
        return ResponseEntity.ok(ReceiverDtoMapper.toDto(updatedReceiver));
    }

    /**
     * Delete a receiver
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReceiver(@PathVariable Long id) throws ReceiverNotFoundException, AccessDeniedException {
        // Verify the receiver exists and user has access to it
        Receiver existingReceiver = receiverService.getReceiverById(id);
        verifyOwnership(existingReceiver);

        receiverService.deleteReceiver(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete multiple receivers
     */
    @PostMapping("/batch-delete")
    public ResponseEntity<Integer> deleteReceivers(@RequestBody BatchDeleteDto request) {
        int deletedCount = receiverService.deleteReceivers(request.getIds());
        return ResponseEntity.ok(deletedCount);
    }


    /**
     * Import receivers from CSV
     */
//    @PostMapping(value = "/import-csv", consumes = {"multipart/form-data"})
//    public ResponseEntity<CsvImportResponseDto> importCsvReceivers(@RequestPart MultipartFile file) throws InvalidCsvFormatException {
//        List<CsvReceiverDto> receivers = csvHelper.parseCsvFile(file);
//        receivers.forEach(receiverDto -> receiverDto.setUserId(getCurrentUserId()));
//        receivers.forEach(System.out::println);
//        CsvImportResponseDto preview = receiverService.importReceivers(receivers);
//        return ResponseEntity.ok(preview);
//    }


    private void verifyUserIdAccess(Long userId) throws AccessDeniedException {
        System.out.println("from validation method");
        System.out.println("userId: " + userId);
        System.out.println("line 170");
        System.out.println("currentUserId: " + this.getCurrentUserId());
        if (!userId.equals(getCurrentUserId())) {
            throw new AccessDeniedException("You do not have permission to set userId");
        }
    }

    private void verifyOwnership(Receiver receiver) throws AccessDeniedException {
        // Regular users can only access their own records
        long userId = getCurrentUserId();
        System.out.println("userId from receiver controller verifying is: " + userId);
        if (!(receiver.getUserId()== userId)) {
            throw new AccessDeniedException("You do not have permission to access this receiver");
        }
    }

    private Long getCurrentUserId() {
        // get user id from jwt token
        System.out.println("auth in line 186: ");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("auth in line 188: " + authentication.getPrincipal().toString());
        if (authentication.getPrincipal() instanceof UserDetails) {
            System.out.println("inside if in line 193");
//            System.out.println(jwt.getSubject());
//            return jwt.getSubject();
            return (long)((User) authentication.getPrincipal()).getId();
        }

        return null;


    }
}
