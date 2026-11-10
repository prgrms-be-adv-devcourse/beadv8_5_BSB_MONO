package com.bukang.product.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.bukang.product.domain.KitOption;
import com.bukang.product.domain.Race;
import com.bukang.product.dto.KitOptionSaveRequestDto;
import com.bukang.product.out.KitOptionRepository;

import lombok.RequiredArgsConstructor;

/**
 * 대회에 키트 옵션 목록을 등록하거나 바꾼다. 키트 옵션 등록·수정 UseCase의 트랜잭션 안에서 부른다 (BUK-137).
 * 요청 목록이 맞는지(중복 등)는 RaceRequestValidator.validateKitOptions가 먼저 검사한다.
 */
@Component
@RequiredArgsConstructor
public class RaceKitOptionUpdater {
	private final KitOptionRepository kitOptionRepository;

	// 새 대회의 옵션을 요청 순서대로 저장한다. 옵션 ID 순서가 등록 순서가 되어 목록 조회가 이 순서로 보여 준다
	public void register(Race race, List<KitOptionSaveRequestDto> requests) {
		kitOptionRepository.saveAll(requests.stream()
			.map(request -> new KitOption(race, request.getName(), request.getValue(), request.getStock()))
			.toList());
	}

	// 요청 목록으로 통째로 바꾼다. (이름, 값)이 같은 기존 옵션은 고치고, 없던 옵션은 더하고, 요청에 없는 옵션은 뺀다
	// 판매 시작 뒤에는 기존 옵션의 준비 수량 늘리기만 된다. 더하기·빼기는 막히고, 이름·값을 바꾸는 것도
	// 옛 옵션을 빼고 새 옵션을 더하는 것과 같아 막힌다 (11/09 결정)
	// 새 옵션은 기존 옵션 뒤에 붙는다 (옵션 ID 순)
	public void replace(Race race, List<KitOptionSaveRequestDto> requests, boolean saleStarted) {
		// 요청과 짝을 맞춘 기존 옵션은 지워 나가고, 끝까지 남은 옵션이 요청에 없는(뺄) 옵션이다
		Map<KitOption.Key, KitOption> remaining = new LinkedHashMap<>();
		kitOptionRepository.findByRaceIdOrderById(race.getId()).forEach(option -> remaining.put(option.key(), option));

		List<KitOption> added = new ArrayList<>();
		for (KitOptionSaveRequestDto request : requests) {
			KitOption option = remaining.remove(KitOption.Key.of(request.getName(), request.getValue()));
			if (option == null) {
				KitOption newOption = new KitOption(race, request.getName(), request.getValue(), request.getStock());
				newOption.checkAddable(saleStarted);
				added.add(newOption);
			} else {
				option.update(request.getName(), request.getValue(), request.getStock(), saleStarted);
			}
		}
		remaining.values().forEach(option -> option.checkRemovable(saleStarted));

		kitOptionRepository.deleteAll(remaining.values());
		// Hibernate는 flush 때 INSERT를 DELETE보다 먼저 실행한다. 지울 옵션의 DELETE를 먼저 내보내
		// 같은 키의 행이 잠깐 둘이 되는 일(유니크 키 위반)이 없게 한다
		kitOptionRepository.flush();
		kitOptionRepository.saveAll(added);
	}
}
