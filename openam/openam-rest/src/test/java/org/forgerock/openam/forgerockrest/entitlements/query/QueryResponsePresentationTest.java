/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */
package org.forgerock.openam.forgerockrest.entitlements.query;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.CountPolicy;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.SortKey;
import org.forgerock.openam.utils.JsonValueBuilder;
import org.forgerock.util.promise.Promise;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class QueryResponsePresentationTest {

    public static final JsonPointer NAME_POINTER = new JsonPointer("name");

    private QueryResourceHandler mockHandler;
    private ArgumentCaptor<ResourceResponse> captor;

    @BeforeMethod
    public void setup() {
        mockHandler = mock(QueryResponseHandler.class);
        captor = ArgumentCaptor.forClass(ResourceResponse.class);

        given(mockHandler.handleResource(captor.capture())).willReturn(true);
    }

    // Handler tests

    @Test
    public void shouldHandleEachQueryItem() throws Exception {
        QueryResponsePresentation.perform(mockHandler, makeEmptyQueryRequest(), makeJsonValues("abc,def,ghj"), NAME_POINTER);
        verify(mockHandler, times(3)).handleResource(any(ResourceResponse.class));
    }

    @Test
    public void shouldHonourQueryResourceHandler() {
        given(mockHandler.handleResource(captor.capture())).willReturn(false);
        QueryResponsePresentation.perform(mockHandler, makeEmptyQueryRequest(), makeJsonValues("abc,def,ghj"), NAME_POINTER);
        verify(mockHandler, times(1)).handleResource(any(ResourceResponse.class)); // handler called first time only.
    }

    // Conversion Tests

    @Test
    public void shouldUsePointerForIDInResourceResponse() {
        JsonPointer pointer = new JsonPointer("name");
        QueryResponsePresentation.perform(mockHandler, makePagedQueryRequest(1, 0), makeJsonValues("abc,def,ghj"), pointer);
        assertThat(extractId(captor.getAllValues(), 0)).isEqualTo("abc");
    }

    @Test
    public void shouldLeaveIDNullIfPointerIncorrect() {
        JsonPointer pointer = new JsonPointer("wibble");
        QueryResponsePresentation.perform(mockHandler, makePagedQueryRequest(1, 0), makeJsonValues("abc,def,ghj"), pointer);
        assertThat(extractId(captor.getAllValues(), 0)).isNull();
    }

    // Paging Tests

    @Test
    public void shouldHandlePageOfResults() throws Exception {
        QueryRequest request = makePagedQueryRequest(1, 0); // page size 1
        QueryResponsePresentation.perform(mockHandler, request, makeJsonValues("abc,def,ghj"), NAME_POINTER);
        assertThat(extractId(captor.getAllValues(), 0)).isEqualTo("abc");
    }

    @Test
    public void shouldHandlePagingOffset()  {
        QueryRequest request = makePagedQueryRequest(1, 1);
        QueryResponsePresentation.perform(mockHandler, request, makeJsonValues("abc,def,ghj"), NAME_POINTER);
        assertThat(extractId(captor.getAllValues(), 0)).isEqualTo("def");
    }

    @Test
    public void shouldHandlePagingOffset2()  {
        QueryResponsePresentation.perform(mockHandler, makePagedQueryRequest(1, 2), makeJsonValues("abc,def,ghj"), NAME_POINTER);
        assertThat(captor.getAllValues().size()).isEqualTo(1);
        assertThat(captor.getValue().getId()).isEqualTo("ghj");
    }

    @Test
    public void shouldOnlyUseOffsetIfPageSizeProvided()  {
        QueryRequest request = makePagedQueryRequest(0, 1);
        QueryResponsePresentation.perform(mockHandler, request, makeJsonValues("abc,def,ghj"), NAME_POINTER);
        assertThat(extractId(captor.getAllValues(), 0)).isNotEqualTo("def");
    }

    @Test
    public void shouldNotApplyPagingIfNoPagingDetailsProvided() {
        QueryRequest request = makePagedQueryRequest(0, 0);
        QueryResponsePresentation.perform(mockHandler, request, makeJsonValues("abc,def,ghj"), NAME_POINTER);
        assertThat(captor.getAllValues().size()).isEqualTo(3);
    }

    @Test
    public void shouldIgnoreNegativeOffsetDuringPagingRequest() {
        QueryRequest request = makePagedQueryRequest(1, -1);
        QueryResponsePresentation.perform(mockHandler, request, makeJsonValues("abc,def,ghj"), NAME_POINTER);
        assertThat(extractId(captor.getAllValues(), 0)).isEqualTo("abc");
    }

    // Query Response

    @Test
    public void shouldMarkPagingPolicyAsEXACTWhenPagingDetailsProvided() throws ResourceException {
        QueryRequest pagedRequest = makePagedQueryRequest(1, 0);
        Promise<QueryResponse, ResourceException> result =
                QueryResponsePresentation.perform(mockHandler, pagedRequest, makeJsonValues("abc,def,ghj"), NAME_POINTER);
        assertThat(result.getOrThrowUninterruptibly().getTotalPagedResultsPolicy()).isEqualTo(CountPolicy.EXACT);
    }

    @Test
    public void shouldCountTotalPagedResultsFromQuery() throws ResourceException {
        QueryRequest pagedRequest = makePagedQueryRequest(1, 0);
        Promise<QueryResponse, ResourceException> result =
                QueryResponsePresentation.perform(mockHandler, pagedRequest, makeJsonValues("abc,def,ghj"), NAME_POINTER);
        assertThat(result.getOrThrowUninterruptibly().getTotalPagedResults()).isEqualTo(3);
    }

    @Test
    public void shouldCountRemainingPagedResultsFromQueryUsingDeprecatedFlag() throws ResourceException {
        QueryRequest deprecatedRequest = makeDeprecatedQueryRequest(1, 0);
        Promise<QueryResponse, ResourceException> result =
                QueryResponsePresentation.perform(mockHandler, deprecatedRequest, makeJsonValues("abc,def,ghj"), NAME_POINTER);
        assertThat(result.getOrThrowUninterruptibly().getRemainingPagedResults()).isEqualTo(2);
    }

    @Test
    public void shouldCountRemainingPagedResultsWhenPageOffsetSpecifiedAndDeprecatedEnabled() throws ResourceException {
        QueryRequest deprecatedRequest = makeDeprecatedQueryRequest(1, 1);
        Promise<QueryResponse, ResourceException> result =
                QueryResponsePresentation.perform(mockHandler, deprecatedRequest, makeJsonValues("abc,def,ghj"), NAME_POINTER);
        assertThat(result.getOrThrowUninterruptibly().getRemainingPagedResults()).isEqualTo(1);
    }

    // Sorting Tests

    @Test
    public void shouldSortResultsBeforeHandling()  {
        QueryRequest sortedRequest = makeSortedQueryRequest("^name");
        QueryResponsePresentation.perform(mockHandler, sortedRequest, makeJsonValues("ghj,def,abc"), NAME_POINTER);
        assertThat(extractId(captor.getAllValues(), 0)).isEqualTo("abc");
    }

    @Test
    public void shouldSortResultsUsingMultiLevelSort() {
        QueryRequest multiLevelRequest = makeSortedQueryRequest("^name", "^place");
        QueryResponsePresentation.perform(mockHandler, multiLevelRequest, makeJsonValues("weasel->forest,badger->town,badger->village"), NAME_POINTER);
        assertThat(captor.getAllValues().get(0).getContent().get("name").asString()).isEqualTo("badger");
        assertThat(captor.getAllValues().get(0).getContent().get("place").asString()).isEqualTo("town");
    }

    @Test
    public void shouldSortResultsUsingMultiLevelWithDescending() {
        QueryRequest descendingRequest = makeSortedQueryRequest("^name", "place");
        QueryResponsePresentation.perform(mockHandler, descendingRequest, makeJsonValues("weasel->forest,badger->town,badger->village"), NAME_POINTER);
        assertThat(captor.getAllValues().get(0).getContent().get("name").asString()).isEqualTo("badger");
        assertThat(captor.getAllValues().get(0).getContent().get("place").asString()).isEqualTo("village");
    }

    @Test
    public void shouldLeaveResultsUnsortedIfErrorDuringSorting() {
        QueryRequest incorrectRequest = makeSortedQueryRequest("^wibble");
        QueryResponsePresentation.perform(mockHandler, incorrectRequest, makeJsonValues("weasel->forest,badger->town,badger->village"), NAME_POINTER);
        assertThat(captor.getAllValues().get(0).getContent().get("name").asString()).isEqualTo("weasel");
    }

    @Test
    public void shouldSortUsingNullValues()  {
        QueryRequest sortedRequest = makeSortedQueryRequest("^name");
        QueryResponsePresentation.perform(mockHandler, sortedRequest, makeJsonValues("NULL,ferret,badger"), NAME_POINTER);
        assertThat(extractId(captor.getAllValues(), 0)).isNull();
    }

    @Test
    public void shouldSortMultiLevelUsingNullValues()  {
        QueryRequest sortedRequest = makeSortedQueryRequest("name", "^place");
        QueryResponsePresentation.perform(mockHandler, sortedRequest, makeJsonValues("weasel->NULL,weasel->woods,badger->forrest"), NAME_POINTER);
        assertThat(extractId(captor.getAllValues(), 0)).isEqualTo("weasel");
        assertThat(captor.getAllValues().get(0).getContent().get("place").asString()).isNull();
    }

    @Test
    public void shouldSortMultiLevelUsingNullValues2()  {
        QueryRequest sortedRequest = makeSortedQueryRequest("name", "place");
        QueryResponsePresentation.perform(mockHandler, sortedRequest, makeJsonValues("weasel->NULL,weasel->woods,badger->forrest"), NAME_POINTER);
        assertThat(extractId(captor.getAllValues(), 0)).isEqualTo("weasel");
        assertThat(captor.getAllValues().get(0).getContent().get("place").asString()).isEqualTo("woods");
    }

    private static QueryRequest makeEmptyQueryRequest() {
        return mock(QueryRequest.class);
    }

    private static QueryRequest makePagedQueryRequest(int size, int offset) {
        QueryRequest request = makeEmptyQueryRequest();
        given(request.getPageSize()).willReturn(size);
        given(request.getPagedResultsOffset()).willReturn(offset);
        return request;
    }

    private QueryRequest makeDeprecatedQueryRequest(int pageSize, int offset) {
        QueryRequest mockRequest = makePagedQueryRequest(pageSize, offset);
        given(mockRequest.getAdditionalParameter("Remaining")).willReturn("true");
        return mockRequest;
    }

    /**
     * Make a QueryRequest based on a simple string encoding:
     * [^][key]
     *
     * caret - Indicates ascending sort, when missing indicates descending.
     * key - The name of the field to sort on.
     */
    private static QueryRequest makeSortedQueryRequest(String... keys) {
        QueryRequest request = makeEmptyQueryRequest();
        List<SortKey> sortKeys = new ArrayList<>();
        for (String key : keys) {
            if (key.startsWith("^")) {
                sortKeys.add(SortKey.ascendingOrder(key.substring(1)));
            } else {
                sortKeys.add(SortKey.descendingOrder(key));
            }
        }
        given(request.getSortKeys()).willReturn(sortKeys);
        return request;
    }

    /**
     * A simple mapping function that takes an encoded string and converts this
     * to a JsonValue.
     *
     * Format:
     *
     * [name]->[place],
     *
     * or simpler:
     *
     * [name],
     *
     * The resultant object will contain a JsonValue of the schema:
     *
     * <code>
     * {
     *   name: "name"
     *   place: "place"
     * }
     * </code>
     *
     * The symbol NULL will be replaced with null.
     */
    private static List<JsonValue> makeJsonValues(String format) {
        List<JsonValue> values = new ArrayList<>();
        for (String item : format.split(",")) {
            String name;
            String place;
            if (item.contains("->")) {
                String[] part = item.split("->");
                name = part[0];
                place = part[1];
            } else {
                name = item;
                place = "";
            }

            if ("NULL".equalsIgnoreCase(name)) {
                name = null;
            }
            if ("NULL".equalsIgnoreCase(place)) {
                place = null;
            }

            values.add(JsonValueBuilder.jsonValue().put("name", name).put("place", place).build());
        }
        return values;
    }

    private static String extractId(List<ResourceResponse> results, int position) {
        return results.get(position).getId();
    }
}