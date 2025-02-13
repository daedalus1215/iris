import { Test, TestingModule } from '@nestjs/testing';
import { AppleTvService } from './apple-tv.service';

describe('AppleTvService', () => {
  let service: AppleTvService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [AppleTvService],
    }).compile();

    service = module.get<AppleTvService>(AppleTvService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });
});
