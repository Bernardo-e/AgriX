package com.sih.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sih.app.R
import com.sih.app.ui.theme.SIHTheme
import kotlinx.coroutines.launch

private data class OnboardingPage(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val iconCdRes: Int,
)

private val onboardingPages = listOf(
    OnboardingPage(
        titleRes = R.string.onboarding_page_1_title,
        bodyRes = R.string.onboarding_page_1_body,
        iconRes = R.drawable.ic_onboarding_soil,
        iconCdRes = R.string.onboarding_page_1_cd,
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_page_2_title,
        bodyRes = R.string.onboarding_page_2_body,
        iconRes = R.drawable.ic_onboarding_recommendations,
        iconCdRes = R.string.onboarding_page_2_cd,
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_page_3_title,
        bodyRes = R.string.onboarding_page_3_body,
        iconRes = R.drawable.ic_onboarding_offline,
        iconCdRes = R.string.onboarding_page_3_cd,
    ),
)

@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCount = onboardingPages.size
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == pageCount - 1

    BackHandler(enabled = currentPage > 0) {
        scope.launch { pagerState.scrollToPage(currentPage - 1) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_progress, currentPage + 1, pageCount),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { index ->
                val selected = index == currentPage
                Box(
                    modifier = Modifier
                        .size(if (selected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        ),
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 16.dp),
            userScrollEnabled = false,
            beyondViewportPageCount = 0,
        ) { page ->
            val item = onboardingPages[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Image(
                    painter = painterResource(item.iconRes),
                    contentDescription = stringResource(item.iconCdRes),
                    modifier = Modifier.size(96.dp),
                )
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Text(
                    text = stringResource(item.bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        Button(
            onClick = {
                if (isLastPage) {
                    onGetStarted()
                } else {
                    scope.launch { pagerState.scrollToPage(currentPage + 1) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(top = 16.dp),
        ) {
            Text(
                text = stringResource(
                    if (isLastPage) R.string.action_get_started else R.string.action_continue,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    SIHTheme {
        OnboardingScreen(onGetStarted = {})
    }
}
