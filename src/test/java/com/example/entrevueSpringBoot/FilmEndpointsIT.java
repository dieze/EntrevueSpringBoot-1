package com.example.entrevueSpringBoot;

import com.example.entrevueSpringBoot.testhelper.InsertBeforeCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.transaction.Transactional;
import java.util.Arrays;

import static com.example.entrevueSpringBoot.testhelper.InsertBeforeCommand.insert;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Black-box testing of {@link FilmController} endpoints.
 *
 * @see TransactionalTestExecutionListener handles @Transactional on Test class or @Test method
 * @see TransactionalTestExecutionListener#isDefaultRollback that rollbacks by default
 */
@IntegrationTest // @SpringBootTest with common configuration
@Transactional
// rollback by default: inserts are not actually committed
// tests can use the same film's titre and auteur's combination of nom & prenom
// TODO: is film's titre unique ?
// TODO: is auteur's combination of nom & prenom unique ?
public class FilmEndpointsIT {

    /**
     * @see #withIds(long[]) for insertions of ids for POST & GET response body assertions.
     */
    //language=json
    private static final String BODY =
            //
            // IntelliJ: Please turn formatter markers on!
            // Preferences -> Editor -> Code Style -> Formatter -> Turn formatter on/off with markers in code comments
            //
            // @formatter:off
            //
            // most compact style to ease search-replace
            // no space around these following JSON structure characters {}[]:,
            // no indent in JSON payload
            // no newline
            "{" +
                // id will be inserted here based on titre TODO: is film's titre unique ?
                "\"titre\":\"Star Wars: The Empire Strikes Back\"," +
                "\"description\":\"Darth Vader is adamant about turning Luke Skywalker to the dark side.\"," +
                "\"acteurs\":[" +
                    "{" + // id will be inserted here based on nom & prenom TODO: is acteur's combination of nom & prenom unique ?
                        "\"nom\":\"Ford\"," +
                        "\"prenom\":\"Harrison\"" +
                    "}," +
                    "{" + // id will be inserted here based on nom & prenom TODO: is acteur's combination of nom & prenom unique ?
                        "\"nom\":\"Hamill\"," +
                        "\"prenom\":\"Mark\"" +
                    "}" +
                "]" +
            "}";
            // @formatter:on

    /**
     * @see InsertBeforeCommand
     */
    private static String withIds(long[] ids) { // 3: film & Ford Harrison & Hamill Mark
        return insert(
                // insert id of film
                String.format("\"id\":%d,", ids[0])
        ).before(
                        // TODO: is film's titre unique ?
                        "\"titre\":\"Star Wars: The Empire Strikes Back\""
                ).andInsert(
                        // insert id of acteur Ford Harisson
                        String.format("\"id\":%d,", ids[1])
                ).before(
                        // TODO: is acteur's combination of nom & prenom unique ?
                        "\"nom\":\"Ford\",\"prenom\":\"Harrison\""
                ).andInsert(
                        // insert id of acteur Hamill Mark
                        String.format("\"id\":%d,", ids[2])
                ).before(
                        // TODO: is acteur's combination of nom & prenom unique ?
                        "\"nom\":\"Hamill\",\"prenom\":\"Mark\""
                )
                .on(BODY);
    }

    @Autowired
    private FilmTestRepository filmTestRepository;

    private long[] findIds() { // 3: film & Ford Harrison & Hamill Mark
        Long[] ids = new Long[3]; // Long[] might have null
        // TODO: is film's titre unique ?
        //noinspection OptionalGetWithoutIsPresent ; let it fail!
        Film film = filmTestRepository.findByTitre("Star Wars: The Empire Strikes Back").get();
        ids[0] = film.getId();
        assertNotNull(ids[0], "null id for film");

        // TODO: is acteur's combination of nom & prenom unique ?

        for (FilmActeur acteur : film.getActeurs()) {
            if ("Ford".equals(acteur.getNom()) && "Harrison".equals(acteur.getPrenom())) ids[1] = acteur.getId();
            if ("Hamill".equals(acteur.getNom()) && "Mark".equals(acteur.getPrenom())) ids[2] = acteur.getId();
        }

        assertNotNull(ids[1], "null id for acteur Ford Harrison");
        assertNotNull(ids[2], "null id for acteur Hamill Mark");

        return Arrays.stream(ids).mapToLong(Long::longValue).toArray(); // long[] do not have null
    }

    @Autowired
    private MockMvc mockMvc;

    public ResultActions doPostFilm() throws Exception {
        return doPostFilm(BODY);
    }

    public ResultActions doPostFilm(String body) throws Exception {
        return mockMvc.perform(
                post("/api/film").contentType("application/json").content(body)
        );
    }

    @Test
    public void postFilm_201() throws Exception {
        doPostFilm()
                .andExpect(status().isCreated()) // 201 Created
                .andExpect(content().contentType("application/json"))
                .andExpect(content().json(withIds(findIds()), true))
        ;
    }

    /**
     * POST same film twice with exact same request body should respond 409 Conflict.
     * <p>
     * TODO: is it valid to POST the exact same film twice ?
     */
    @Test
    public void postFilm_twiceExactSame_409() throws Exception {
        doPostFilm(); // titre: "Star Wars: The Empire Strikes Back"
        doPostFilm() // titre: "Star Wars: The Empire Strikes Back"
                .andExpect(status().isConflict()) // 409 Conflict
                .andExpect(content().contentTypeCompatibleWith("text/plain")) // #contentTypeCompatibleWith -> issue with ";charset=UTF-8"
        //.andExpect(content().string(org.hamcrest.Matchers.containsString("Unique index or primary key violation"))) // don't uncomment ; help for dev must not be tested
        ;
    }

    /**
     * POST same film twice but with different acteurs should respond 409 Conflict.
     * <p>
     * Assumption: POST film can't be used to add/change a film's acteurs. This would be the responsibility of a PUT film.
     * <p>
     * TODO: can we change an existing film's acteurs on POST film ?
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void postFilm_twiceDifferentActeurs_409(String testCaseName, String toReplace, String replacement) throws Exception {
        doPostFilm(); // first POST film
        long[] ids = findIds();

        // trying to change film's acteurs
        doPostFilm(BODY.replace(toReplace, replacement))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("text/plain")) // #contentTypeCompatibleWith -> issue with ";charset=UTF-8"
        ;

        expectDefaultFilm(ids); // unchanged from first POST film
    }

    static Object[][] postFilm_twiceDifferentActeurs_409() {
        return new Object[][]{
                // testCaseName, toReplace, replacement
                new String[]{
                        "No acteur (absent)", // i.e. removing film's acteurs?
                        ",\"acteurs\":[{\"nom\":\"Ford\",\"prenom\":\"Harrison\"},{\"nom\":\"Hamill\",\"prenom\":\"Mark\"}]",
                        "",
                },
                new String[]{
                        "No acteur (null)", // i.e. removing film's acteurs?
                        ",\"acteurs\":[{\"nom\":\"Ford\",\"prenom\":\"Harrison\"},{\"nom\":\"Hamill\",\"prenom\":\"Mark\"}]",
                        ",\"acteurs\":null",
                },
                new String[]{
                        "No acteur (empty)",// i.e. removing film's acteurs?
                        ",\"acteurs\":[{\"nom\":\"Ford\",\"prenom\":\"Harrison\"},{\"nom\":\"Hamill\",\"prenom\":\"Mark\"}]",
                        ",\"acteurs\":[]",
                },
                new String[]{
                        "Only the first acteur",// i.e. replacing film's acteurs?
                        ",\"acteurs\":[{\"nom\":\"Ford\",\"prenom\":\"Harrison\"},{\"nom\":\"Hamill\",\"prenom\":\"Mark\"}]",
                        ",\"acteurs\":[{\"nom\":\"Ford\",\"prenom\":\"Harrison\"}]",
                },
                new String[]{
                        "A single different acteur",// i.e. replacing film's acteurs?
                        ",\"acteurs\":[{\"nom\":\"Ford\",\"prenom\":\"Harrison\"},{\"nom\":\"Hamill\",\"prenom\":\"Mark\"}]",
                        ",\"acteurs\":[{\"nom\":\"Fisher\",\"prenom\":\"Carrie\"}]",
                },
                new String[]{
                        "Film's acteurs plus a new one",// i.e. adding a new acteur?
                        ",\"acteurs\":[{\"nom\":\"Ford\",\"prenom\":\"Harrison\"},{\"nom\":\"Hamill\",\"prenom\":\"Mark\"}]",
                        ",\"acteurs\":[{\"nom\":\"Ford\",\"prenom\":\"Harrison\"},{\"nom\":\"Hamill\",\"prenom\":\"Mark\"}" +
                                ",{\"nom\":\"Fisher\",\"prenom\":\"Carrie\"}" +
                                "]",
                },
        };
    }

    @Test
    public void getFilm_200() throws Exception {
        doPostFilm();
        long[] ids = findIds();
        expectDefaultFilm(ids);
    }

    /**
     * @see #BODY the default film
     */
    private void expectDefaultFilm(long[] ids) throws Exception {
        mockMvc.perform(
                        get("/api/film/{id}", ids[0])
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().json(withIds(ids), true))
        ;
    }

    // not specified on the instructions
    // but it makes sense to respond 404 for non-existing film
    @Test
    public void getFilm_404() throws Exception {
        // let's assume that some other previous test might have written to
        // the database without rollback ; in this context using a random id
        // might lead to an existing Film thus responding 200 instead of expected 404
        //
        // we won't load the entire database just to know all the ids, to pick one that do
        // not exist (and if parallel execution, id might have been created meanwhile...)
        //
        // negative or zero id might fail if @Min(1) validation is in place

        // black-box 100% safe solution is to create a Film,
        // remove it from database (using test-only facilities since production do not provide DELETE),
        // then query the removed Film

        doPostFilm();
        long[] ids = findIds(); // 3: film & Ford Harrison & Hamill Mark
        filmTestRepository.deleteById(ids[0]);

        mockMvc.perform(
                        get("/api/film/{id}", ids[0])
                )
                .andExpect(status().isNotFound())
                .andExpect(content().string("")); // empty response body
    }
}
