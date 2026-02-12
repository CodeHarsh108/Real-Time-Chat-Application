package com.harsh.chat.controllers;


import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.Room;
import com.harsh.chat.payload.MessageRequest;
import com.harsh.chat.repositories.MessageRepository;
import com.harsh.chat.repositories.RoomRepository;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class ChatController {


    private RoomRepository roomRepository;

    private MessageRepository messageRepository;

    public ChatController(RoomRepository roomRepository, MessageRepository messageRepository) {
        this.roomRepository = roomRepository;
        this.messageRepository = messageRepository;
    }


    @MessageMapping("/sendMessage/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public Message sendMessage(
            @DestinationVariable String roomId,
            @RequestBody MessageRequest request
    ) {

        Room room = roomRepository.findByRoomId(request.getRoomId())
                .orElseThrow(() -> new RuntimeException("Room not found!!!"));
        Message message = Message.create(
                request.getRoomId(),
                request.getSender(),
                request.getContent()
        );
        message = messageRepository.save(message);
        room.addMessage(message.getId());
        roomRepository.save(room);
        return message;


    }
}