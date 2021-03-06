package com.blogspot.sontx.chatsocket.server.model.account;

import com.blogspot.sontx.chatsocket.lib.bean.AccountInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import lombok.extern.log4j.Log4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Log4j
public class JsonAccountStorage implements AccountStorage {
    private final File storeFile;
    private List<Account> accounts;

    public JsonAccountStorage(String storeFile) throws IOException {
        this.storeFile = new File(storeFile);
        if (ensureStoreLocation())
            load();
    }

    private void load() throws IOException {
        if (!storeFile.isFile()) {
            accounts = new CopyOnWriteArrayList<>();
        } else {
            CollectionType destType = TypeFactory.defaultInstance().constructCollectionType(List.class, Account.class);
            List<Account> accounts = new ObjectMapper().readValue(storeFile, destType);
            accounts.forEach(account -> {
                account.getDetail().setAccountId(account.getId());
                account.getDetail().setState(AccountInfo.STATE_OFFLINE);
            });
            this.accounts = new CopyOnWriteArrayList<>(accounts);
        }
    }

    private boolean ensureStoreLocation() {
        File dir = storeFile.getAbsoluteFile().getParentFile();
        return dir.exists() || dir.mkdirs();
    }

    @Override
    public Optional<Account> findById(int id) {
        return accounts.stream().filter(account -> account.getId() == id).findFirst();
    }

    @Override
    public Optional<Account> findByUserName(String username) {
        return accounts.stream().filter(account -> account.getUsername().equals(username)).findFirst();
    }

    @Override
    public Account add(Account account) {
        reinforce(account);
        accounts.add(account);
        saveToFile();
        return account;
    }

    private void reinforce(Account account) {
        int freeId = computeFreeId();
        account.setId(freeId);
        account.getDetail().setAccountId(freeId);
    }

    private int computeFreeId() {
        int maxId = -1;
        for (Account account : accounts) {
            maxId = Math.max(account.getId(), maxId);
        }
        return maxId + 1;
    }

    private synchronized void saveToFile() {
        try {
            if (storeFile.exists()) {
                if (!storeFile.delete()) {
                    log.error("Can not delete store file.");
                    return;
                }
            }

            new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .writeValue(storeFile, accounts);

        } catch (IOException e) {
            log.error("Error while saving user info to file", e);
        }
    }

    @Override
    public List<Account> findAll() {
        return new ArrayList<>(accounts);
    }

    @Override
    public void updateDetail(int accountId, AccountInfo accountInfo) {
        accounts
                .stream()
                .filter(account -> account.getId() == accountId)
                .findFirst()
                .ifPresent((Account account) -> updateDetail(account, accountInfo));

        saveToFile();
    }

    private void updateDetail(Account account, AccountInfo detail) {
        account.getDetail().setDisplayName(detail.getDisplayName());
        account.getDetail().setStatus(detail.getStatus());
    }

    @Override
    public void updatePasswordHash(int accountId, String passwordHash) {
        accounts
                .stream()
                .filter(account -> account.getId() == accountId)
                .findFirst()
                .ifPresent((Account account) -> account.setPasswordHash(passwordHash));

        saveToFile();
    }
}
