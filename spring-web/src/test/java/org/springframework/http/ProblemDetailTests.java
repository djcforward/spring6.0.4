/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProblemDetail}.
 *
 * @author Juergen Hoeller
 */
class ProblemDetailTests {

	@Test
	void equalsAndHashCode() {
		ProblemDetail pd1 = ProblemDetail.forStatus(500);
		ProblemDetail pd2 = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		ProblemDetail pd3 = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		ProblemDetail pd4 = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "some detail");

		assertThat(pd1).isEqualTo(pd2);
		assertThat(pd2).isEqualTo(pd1);
		assertThat(pd1.hashCode()).isEqualTo(pd2.hashCode());

		assertThat(pd3).isNotEqualTo(pd4);
		assertThat(pd4).isNotEqualTo(pd3);
		assertThat(pd3.hashCode()).isNotEqualTo(pd4.hashCode());

		assertThat(pd1).isNotEqualTo(pd3);
		assertThat(pd1).isNotEqualTo(pd4);
		assertThat(pd2).isNotEqualTo(pd3);
		assertThat(pd2).isNotEqualTo(pd4);
		assertThat(pd1.hashCode()).isNotEqualTo(pd3.hashCode());
		assertThat(pd1.hashCode()).isNotEqualTo(pd4.hashCode());
	}

}
