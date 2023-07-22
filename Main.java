package com.yourcompany.apitests;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {

    public static void main(String[] args) {
        try {
            // Создание экземпляра бота
            TelegramBot bot = new TelegramBot();

            // Регистрация бота в TelegramBotsApi
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);

            // Опционально, можно вывести сообщение о запуске бота
            System.out.println("Бот успешно запущен! Ожидание команд...");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
