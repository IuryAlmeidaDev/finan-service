package dev.iury.lifeos.finance.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class PlanningListRegressionTest {

    @Test
    void emptyBudgetsAreReturnedAsAnEmptyList() {
        given()
                .when().get("/api/finance/budgets")
                .then()
                .statusCode(200)
                .body("", empty());
    }

    @Test
    void emptyIncomeGoalsAreReturnedAsAnEmptyList() {
        given()
                .when().get("/api/finance/income-goals")
                .then()
                .statusCode(200)
                .body("", empty());
    }
}
