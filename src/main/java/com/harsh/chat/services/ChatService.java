package com.harsh.chat.services;

import com.harsh.chat.dto.MessageDTO;
import com.harsh.chat.dto.RoomDTO;
import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.Room;
import com.harsh.chat.exceptions.RoomNotFoundException;
import com.harsh.chat.payload.MessageRequest;
import com.harsh.chat.repositories.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final RoomRepository roomRepository;

    public Room createRoom(String roomId) {
        if (roomRepository.findByRoomId(roomId) != null) {
            throw new IllegalArgumentException("Room already exists");
        }

        Room room = new Room();
        room.setRoomId(roomId);
        room.setCreatedAt(LocalDateTime.now());
        room.setLastActivity(LocalDateTime.now());
        return roomRepository.save(room);
    }

    public Room getRoom(String roomId) {
        Room room = roomRepository.findByRoomId(roomId);
        if (room == null) {
            throw new RoomNotFoundException("Room not found with ID: " + roomId);
        }
        return room;
    }

    public Message sendMessage(MessageRequest request) {
        logger.info("Sending message from {} to room {}",
                request.getSender(), request.getRoomId());
        Room room = getRoom(request.getRoomId());

        Message message = new Message();
        message.setContent(request.getContent());
        message.setSender(request.getSender());
        message.setTimeStamp(LocalDateTime.now());

        room.getMessages().add(message);
        room.setLastActivity(LocalDateTime.now());
        roomRepository.save(room);

        return message;
    }

    public Page<Message> getMessages(String roomId, Pageable pageable) {
        Room room = getRoom(roomId);
        List<Message> messages = room.getMessages();

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), messages.size());

        if (start > messages.size()) {
            return Page.empty(pageable);
        }

        return new PageImpl<>(
                messages.subList(start, end),
                pageable,
                messages.size()
        );
    }

    public List<RoomDTO> getAllRooms() {
        List<Room> rooms = roomRepository.findAll();
        return rooms.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private RoomDTO convertToDTO(Room room) {
        RoomDTO dto = new RoomDTO();
        dto.setRoomId(room.getRoomId());
        dto.setCreatedAt(room.getCreatedAt());
        dto.setLastActivity(room.getLastActivity());
        dto.setMessageCount(room.getMessages().size());

        // Get recent 5 messages
        List<Message> recentMessages = room.getMessages().stream()
                .sorted((m1, m2) -> m2.getTimeStamp().compareTo(m1.getTimeStamp()))
                .limit(5)
                .collect(Collectors.toList());

        dto.setRecentMessages(recentMessages.stream()
                .map(m -> new MessageDTO(m.getSender(), m.getContent(),
                        m.getTimeStamp(), room.getRoomId()))
                .collect(Collectors.toList()));

        return dto;
    }
}