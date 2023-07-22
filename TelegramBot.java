package com.yourcompany.apitests;

import io.github.cdimascio.dotenv.Dotenv;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TelegramBot extends TelegramLongPollingBot {

    private static final String START_TEXT = "привет! Нажми кнопку для запуска автотестов.";
    private static final String BUTTON_TEXT_RUN_TESTS = "Клиентский API";
    private static final String BUTTON_DATA_RUN_TESTS = "run_tests";
    static final String BUTTON_TEXT_BACK = "Назад";
    static final String BUTTON_DATA_BACK = "back";

    Dotenv dotenv = Dotenv.load();

    private boolean runningTests = false; // Флаг, чтобы отслеживать, выполняются ли тесты

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            // Обработка входящих сообщений от пользователя
            handleMessage(update.getMessage().getChatId(), String.valueOf(update.getMessage().getChat().getFirstName()  + " (" + update.getMessage().getChat().getUserName() + ")"), update.getMessage().getText());
        } else if (update.hasCallbackQuery()) {
            // Обработка нажатия на inline-кнопку
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private void handleMessage(Long chatId, String userName, String text) {
        if (text.equals("/start")) {
            // Ответ на команду /start
            sendTextMessageWithInlineKeyboard(chatId, userName + ", " + START_TEXT);
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String buttonData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String userName = callbackQuery.getMessage().getChat().getFirstName() + " (" + callbackQuery.getMessage().getChat().getUserName() + ")";

        if (BUTTON_DATA_RUN_TESTS.equals(buttonData)) {
            // Нажата кнопка "Клиентский API"
            if (!runningTests) {
                // Запускаем тесты, если они не выполняются в данный момент
                runningTests = true; // Устанавливаем флаг в true, чтобы предотвратить запуск новых тестов
                runTests(chatId, messageId);
            }
        } else if (BUTTON_DATA_BACK.equals(buttonData)) {
            // Нажата кнопка "Назад"
            runningTests = false; // Сбрасываем флаг, чтобы позволить запуск новых тестов

            // Задержка в 5 секунд перед переходом на кнопку "Клиентский API"
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.schedule(() -> {
                editMessageText(chatId, userName + ", " + START_TEXT, createInlineKeyboard(BUTTON_TEXT_RUN_TESTS, BUTTON_DATA_RUN_TESTS), messageId);
            }, 5, TimeUnit.SECONDS);
        }

        // Ответ на нажатие inline-кнопки
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        try {
            execute(answer);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void runTests(Long chatId, Integer messageID) {
        ApiTest apiTest = new ApiTest();
        apiTest.setUp();

        // Переменные для хранения результатов тестов
        StringBuilder allTestResults = new StringBuilder();
        boolean allTestsPassed = true;

        // Список тестов, которые необходимо запустить
        List<TestInfo> tests = new ArrayList<>();
        tests.add(new TestInfo("1. Совершение исходящего вызова", () -> apiTest.testOutgoingCall()));
        tests.add(new TestInfo("2. Экспорт адресной книги домена", () -> apiTest.testExportAddressBook()));

        for (TestInfo testInfo : tests) {
            try {
                testInfo.runTest();
                String testReport = apiTest.getReport();
                appendTestResult(allTestResults, testInfo.getTestName(), testReport);

                // Проверяем результаты теста
                if (testReport.contains("Тест провален")) {
                    allTestsPassed = false;
                }
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        apiTest.tearDown();
        runningTests = false; // Завершили выполнение тестов

        // Отправляем результаты в телеграмм
        if (allTestsPassed) {
            editMessageText(chatId, "✅ Тестирование пройдено успешно:" + allTestResults.toString(), createInlineKeyboard(BUTTON_TEXT_BACK, BUTTON_DATA_BACK), messageID);
        } else {
            editMessageText(chatId, "❌ Тестирование провалено:" + allTestResults.toString(), createInlineKeyboard(BUTTON_TEXT_BACK, BUTTON_DATA_BACK), messageID);
        }
    }

    private void appendTestResult(StringBuilder allTestResults, String testName, String testReport) {
        allTestResults.append("\n\n").append(testName).append("\n").append(testReport);
    }

    void sendTextMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void editMessageText(Long chatId, String text, InlineKeyboardMarkup replyMarkup, Integer messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId); // messageId - идентификатор сообщения, которое нужно изменить
        message.setText(text);
        message.setReplyMarkup(replyMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendTextMessageWithInlineKeyboard(Long chatId, String text) {
        // Отправляем сообщение с inline-клавиатурой
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(createInlineKeyboard(BUTTON_TEXT_RUN_TESTS, BUTTON_DATA_RUN_TESTS));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardMarkup createInlineKeyboard(String buttonText, String buttonData) {
        // Создаем inline-кнопку
        InlineKeyboardButton inlineButton = new InlineKeyboardButton();
        inlineButton.setText(buttonText);
        inlineButton.setCallbackData(buttonData);

        // Создаем список кнопок и добавляем в него inline-кнопку
        List<InlineKeyboardButton> rowButtons = new ArrayList<>();
        rowButtons.add(inlineButton);

        // Создаем список списков кнопок (строки) и добавляем в него список кнопок
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(rowButtons);

        // Создаем объект разметки с кнопками и добавляем его в сообщение
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(rows);

        return keyboardMarkup;
    }

    @Override
    public String getBotUsername() {
        // Имя бота
        return dotenv.get("BOT_USERNAME");
    }

    @Override
    public String getBotToken() {
        // Токен бота
        return dotenv.get("BOT_TOKEN");
    }

    public void clearWebhook() {
        try {
            DeleteWebhook deleteWebhook = new DeleteWebhook();
            execute(deleteWebhook);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private interface TestRunner {
        void runTest() throws NoSuchMethodException;
    }

    private static class TestInfo {
        private final String testName;
        private final TestRunner testRunner;

        public TestInfo(String testName, TestRunner testRunner) {
            this.testName = testName;
            this.testRunner = testRunner;
        }

        public String getTestName() {
            return testName;
        }

        public void runTest() throws NoSuchMethodException {
            testRunner.runTest();
        }
    }
}
