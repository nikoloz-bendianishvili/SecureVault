package com.example.SecureVault.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DocumentIntegrationTest extends IntegrationTestBase {

    private ResultActions createDocument(Tokens tokens, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/documents")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"text\":\"secret text\"}".formatted(name)));
    }

    private ResultActions getDocument(Tokens tokens, Long documentId) throws Exception {
        return mockMvc.perform(get("/api/v1/documents/" + documentId).header(HttpHeaders.AUTHORIZATION, bearer(tokens)));
    }

    private ResultActions updateDocument(Tokens tokens, Long documentId, String json) throws Exception {
        return mockMvc.perform(patch("/api/v1/documents/" + documentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions deleteDocument(Tokens tokens, Long documentId) throws Exception {
        return mockMvc.perform(delete("/api/v1/documents/" + documentId).header(HttpHeaders.AUTHORIZATION, bearer(tokens)));
    }

    private ResultActions share(Tokens tokens, Long documentId, Long userId) throws Exception {
        return mockMvc.perform(post("/api/v1/documents/" + documentId + "/shares")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":%d}".formatted(userId)));
    }

    private ResultActions revokeShare(Tokens tokens, Long documentId, Long userId) throws Exception {
        return mockMvc.perform(delete("/api/v1/documents/" + documentId + "/shares/" + userId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens)));
    }

    @Test
    public void create_validDocument_returns201WithBody() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());

        createDocument(owner, "my document")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("my document"))
                .andExpect(jsonPath("$.text").value("secret text"));
    }

    @Test
    public void create_blankName_returns400() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());

        createDocument(owner, "").andExpect(status().isBadRequest());
    }

    @Test
    public void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/documents").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"n\",\"text\":\"t\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void list_returnsOnlyOwnDocuments() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());
        Tokens other = createVerifiedUser(uniqueEmail());
        createDocument(owner, "first");
        createDocument(owner, "second");
        createDocument(other, "foreign");

        mockMvc.perform(get("/api/v1/documents").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    public void list_unknownSortProperty_returns400() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());

        mockMvc.perform(get("/api/v1/documents?sort=bogus").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void get_owner_returns200() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());
        Long documentId = idFrom(createDocument(owner, "doc"));

        getDocument(owner, documentId).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("doc"));
    }

    @Test
    public void get_otherUserWithoutAccess_returns404() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());
        Tokens stranger = createVerifiedUser(uniqueEmail());
        Long documentId = idFrom(createDocument(owner, "doc"));

        getDocument(stranger, documentId).andExpect(status().isNotFound());
    }

    @Test
    public void get_unknownDocument_returns404() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());

        getDocument(owner, 999999L).andExpect(status().isNotFound());
    }

    @Test
    public void update_owner_returns200WithChangedFields() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());
        Long documentId = idFrom(createDocument(owner, "doc"));

        updateDocument(owner, documentId, "{\"name\":\"renamed\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("renamed"))
                .andExpect(jsonPath("$.text").value("secret text"));
    }

    @Test
    public void update_otherUserWithoutAccess_returns404() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());
        Tokens stranger = createVerifiedUser(uniqueEmail());
        Long documentId = idFrom(createDocument(owner, "doc"));

        updateDocument(stranger, documentId, "{\"name\":\"hacked\"}").andExpect(status().isNotFound());
    }

    @Test
    public void delete_owner_returns204AndDocumentIsGone() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());
        Long documentId = idFrom(createDocument(owner, "doc"));

        deleteDocument(owner, documentId).andExpect(status().isNoContent());

        getDocument(owner, documentId).andExpect(status().isNotFound());
    }

    @Test
    public void sharedUser_canReadButNotUpdateOrDelete() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());
        String readerEmail = uniqueEmail();
        Tokens reader = createVerifiedUser(readerEmail);
        Long documentId = idFrom(createDocument(owner, "doc"));

        share(owner, documentId, findUser(readerEmail).getId()).andExpect(status().isNoContent());

        getDocument(reader, documentId).andExpect(status().isOk());
        updateDocument(reader, documentId, "{\"name\":\"changed\"}").andExpect(status().isForbidden());
        deleteDocument(reader, documentId).andExpect(status().isForbidden());
    }

    @Test
    public void sharedUser_cannotShareFurther() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());
        String readerEmail = uniqueEmail();
        Tokens reader = createVerifiedUser(readerEmail);
        String thirdEmail = uniqueEmail();
        createVerifiedUser(thirdEmail);
        Long documentId = idFrom(createDocument(owner, "doc"));
        share(owner, documentId, findUser(readerEmail).getId());

        share(reader, documentId, findUser(thirdEmail).getId()).andExpect(status().isForbidden());
    }

    @Test
    public void revokeShare_userLosesAccess() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());
        String readerEmail = uniqueEmail();
        Tokens reader = createVerifiedUser(readerEmail);
        Long readerId = findUser(readerEmail).getId();
        Long documentId = idFrom(createDocument(owner, "doc"));
        share(owner, documentId, readerId);

        revokeShare(owner, documentId, readerId).andExpect(status().isNoContent());

        getDocument(reader, documentId).andExpect(status().isNotFound());
    }

    @Test
    public void share_duplicate_returns409() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());
        String readerEmail = uniqueEmail();
        createVerifiedUser(readerEmail);
        Long readerId = findUser(readerEmail).getId();
        Long documentId = idFrom(createDocument(owner, "doc"));
        share(owner, documentId, readerId).andExpect(status().isNoContent());

        share(owner, documentId, readerId).andExpect(status().isConflict());
    }

    @Test
    public void share_withYourself_returns400() throws Exception {
        String ownerEmail = uniqueEmail();
        Tokens owner = createVerifiedUser(ownerEmail);
        Long documentId = idFrom(createDocument(owner, "doc"));

        share(owner, documentId, findUser(ownerEmail).getId()).andExpect(status().isBadRequest());
    }

    @Test
    public void share_unknownUser_returns404() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());
        Long documentId = idFrom(createDocument(owner, "doc"));

        share(owner, documentId, 999999L).andExpect(status().isNotFound());
    }

    @Test
    public void admin_hasNoAccessToOtherUsersDocuments() throws Exception {
        Tokens owner = createVerifiedUser(uniqueEmail());
        Long documentId = idFrom(createDocument(owner, "doc"));

        getDocument(loginAsAdmin(), documentId).andExpect(status().isNotFound());
    }
}
